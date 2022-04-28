(ns ring.adapter.jetty
  "A Ring adapter that uses the Jetty 11+ embedded web server.

  Adapters are used to convert Ring handlers into running web servers."
  (:require [clojure.string :as string]
            [ring.core.protocols :as protocols])
  (:import [java.util Locale]
           [org.eclipse.jetty.server
            Request
            Server
            ServerConnector
            ConnectionFactory
            HttpConfiguration
            HttpConnectionFactory]
           [org.eclipse.jetty.server.handler AbstractHandler]
           [org.eclipse.jetty.util BlockingArrayQueue]
           [org.eclipse.jetty.util.thread ThreadPool QueuedThreadPool]
           [jakarta.servlet DispatcherType]
           [jakarta.servlet.http HttpServletRequest HttpServletResponse]))

(defn- get-headers
  "Creates a name/value map of all the request headers."
  [^HttpServletRequest request]
  (reduce
    (fn [headers, ^String name]
      (assoc headers
        (.toLowerCase name Locale/ENGLISH)
        (->> (.getHeaders request name)
             (enumeration-seq)
             (string/join ","))))
    {}
    (enumeration-seq (.getHeaderNames request))))

(defn- get-content-length
  "Returns the content length, or nil if there is no content."
  [^HttpServletRequest request]
  (let [length (.getContentLength request)]
    (if (>= length 0) length)))

(defn- get-client-cert
  "Returns the SSL client certificate of the request, if one exists."
  [^HttpServletRequest request]
  (first (.getAttribute request "javax.servlet.request.X509Certificate")))

(defn build-request-map
  "Create the request map from the HttpServletRequest object."
  [^HttpServletRequest request]
  {:server-port        (.getServerPort request)
   :server-name        (.getServerName request)
   :remote-addr        (.getRemoteAddr request)
   :uri                (.getRequestURI request)
   :query-string       (.getQueryString request)
   :scheme             (keyword (.getScheme request))
   :request-method     (keyword (.toLowerCase (.getMethod request) Locale/ENGLISH))
   :protocol           (.getProtocol request)
   :headers            (get-headers request)
   :content-type       (.getContentType request)
   :content-length     (get-content-length request)
   :character-encoding (.getCharacterEncoding request)
   :ssl-client-cert    (get-client-cert request)
   :body               (.getInputStream request)})

(defn- set-headers
  "Update a HttpServletResponse with a map of headers."
  [^HttpServletResponse response, headers]
  (doseq [[key val-or-vals] headers]
    (if (string? val-or-vals)
      (.setHeader response key val-or-vals)
      (doseq [val val-or-vals]
        (.addHeader response key val))))
  ; Some headers must be set through specific methods
  (when-let [content-type (get headers "Content-Type")]
    (.setContentType response content-type)))

(defn update-servlet-response
  "Update the HttpServletResponse using a response map."
  ([^HttpServletResponse response response-map]
   (let [{:keys [status headers body]} response-map]
     (when (nil? response)
       (throw (NullPointerException. "HttpServletResponse is nil")))
     (when (nil? response-map)
       (throw (NullPointerException. "Response map is nil")))
     (when status
       (.setStatus response status))
     (set-headers response headers)
     (let [output-stream (.getOutputStream response)]
       (protocols/write-body-to-stream body response-map output-stream)))))

(defn- ^AbstractHandler proxy-handler [handler]
  (proxy [AbstractHandler] []
    (handle [_ ^Request base-request ^HttpServletRequest request response]
      (when-not (= (.getDispatcherType request) DispatcherType/ERROR)
        (let [request-map  (build-request-map request)
              response-map (handler request-map)]
          (update-servlet-response response response-map)
          (.setHandled base-request true))))))

(defn- ^ServerConnector server-connector [^Server server & factories]
  (ServerConnector. server #^"[Lorg.eclipse.jetty.server.ConnectionFactory;" (into-array ConnectionFactory factories)))

(defn- ^HttpConfiguration http-config [options]
  (doto (HttpConfiguration.)
    (.setSendDateHeader (:send-date-header? options true))
    (.setOutputBufferSize (:output-buffer-size options 32768))
    (.setRequestHeaderSize (:request-header-size options 8192))
    (.setResponseHeaderSize (:response-header-size options 8192))
    (.setSendServerVersion (:send-server-version? options true))))

(defn- ^ServerConnector http-connector [server options]
  (let [http-factory (HttpConnectionFactory. (http-config options))]
    (doto (server-connector server http-factory)
      (.setPort (options :port 80))
      (.setHost (options :host))
      (.setIdleTimeout (options :max-idle-time 200000)))))

(defn- ^ThreadPool create-threadpool [options]
  (let [min-threads         (options :min-threads 8)
        max-threads         (options :max-threads 50)
        queue-max-capacity  (-> (options :max-queued-requests Integer/MAX_VALUE) (max 8))
        queue-capacity      (-> min-threads (max 8) (min queue-max-capacity))
        blocking-queue      (BlockingArrayQueue. queue-capacity
                              queue-capacity
                              queue-max-capacity)
        thread-idle-timeout (options :thread-idle-timeout 60000)
        pool                (QueuedThreadPool. max-threads
                              min-threads
                              thread-idle-timeout
                              blocking-queue)]
    (when (:daemon? options false)
      (.setDaemon pool true))
    pool))

(defn- ^Server create-server [options]
  (let [pool   (or (:thread-pool options) (create-threadpool options))
        server (Server. pool)]
    (.addConnector server (http-connector server options))
    server))

(defn ^Server run-jetty
  "Start a Jetty webserver to serve the given handler according to the
  supplied options:

  :configurator           - a function called with the Jetty Server instance
  :port                   - the port to listen on (defaults to 80)
  :host                   - the hostname to listen on
  :join?                  - blocks the thread until server ends
                            (defaults to true)
  :daemon?                - use daemon threads (defaults to false)
  :thread-pool            - custom thread pool instance for Jetty to use
  :max-threads            - the maximum number of threads to use (default 50)
  :min-threads            - the minimum number of threads to use (default 8)
  :max-queued-requests    - the maximum number of requests to be queued
  :thread-idle-timeout    - Set the maximum thread idle time. Threads that are
                            idle for longer than this period may be stopped
                            (default 60000)
  :max-idle-time          - the maximum idle time in milliseconds for a
                            connection (default 200000)
  :send-date-header?      - add a date header to the response (default true)
  :output-buffer-size     - the response body buffer size (default 32768)
  :request-header-size    - the maximum size of a request header (default 8192)
  :response-header-size   - the maximum size of a response header (default 8192)
  :send-server-version?   - add Server header to HTTP response (default true)"
  [handler options]
  (let [server (create-server (dissoc options :configurator))]
    (.setHandler server (proxy-handler handler))
    (when-let [configurator (:configurator options)]
      (configurator server))
    (try
      (.start server)
      (when (:join? options true)
        (.join server))
      server
      (catch Exception ex
        (.stop server)
        (throw ex)))))
