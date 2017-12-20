(ns leiningen.jaq
  (:require
   [clojure.tools.logging :as log]
   [clojure.pprint :refer [pprint]]
   [clojure.java.io :as io]
   [cemerick.pomegranate.aether :as aether]
   [leiningen.core.eval :refer [eval-in-project]]
   [leiningen.ring.uberwar :refer [uberwar]]
   [leiningen.ring.war :refer [war-file-path]]
   [leiningen.ring.uberwar :refer [default-uberwar-name]]
   [leiningen.help :refer [help-for subtask-help-for]]
   [jaq.services.auth :as auth]
   [jaq.services.memcache :as memcache]
   [jaq.services.storage :as storage]
   [jaq.services.resource :as resource]
   [jaq.services.util :refer [sleep]]
   [jaq.services.appengine-admin :as admin])
  (:import
   [java.io File]
   [com.google.appengine.tools KickStart]
   [com.google.appengine.tools.util Action]
   [com.google.appengine.tools.development DevAppServerMain DevAppServerFactory]
   [com.google.appengine.api.memcache
    MemcacheService
    MemcacheServiceFactory]
   [com.google.appengine.tools.remoteapi
    RemoteApiInstaller
    RemoteApiOptions]
   [net.lingala.zip4j.core ZipFile]
   [net.lingala.zip4j.exception ZipException]))

(defn delete-recursively [fname]
  (doseq [f (reverse (file-seq (clojure.java.io/file fname)))]
    (clojure.java.io/delete-file f)))

(defn copy-file [source-path dest-path]
  (io/copy (io/file source-path) (io/file dest-path)))

(defn unzip-file
  [zip dest]
  (try
    (-> (ZipFile. zip)
        (.extractAll dest))
    (catch ZipException e
      (log/info "Error" e)
      (.printStackTrace e)
      e)))

(defn get-sdk-file [coords project]
  (->
    (aether/resolve-dependencies
     :coordinates [coords]
     :offline? false)
    first
    first
    meta
    :file))

(defn get-sdk-version [project]
  (get-in project [:jaq :sdk-version]))

(defn get-target-sdk-dir [project]
  (get-in project [:jaq :sdk-path] "sdk"))

(defn unpack
  "Unpack the App Engine SDK."
  [project & args]
  (let [deps (:dependencies project)
        f (get-sdk-file ['com.google.appengine/appengine-java-sdk (get-sdk-version project) :extension "zip"] project)
        path (.getAbsolutePath f)
        target-dir (get-target-sdk-dir project)]
    (unzip-file path target-dir)))

(defn get-exploded-war-path [project]
  (get-in project [:jaq :war-app-path] "war"))

(defn get-sdk-path [project]
  (str (get-in project [:jaq :sdk-path] "sdk") (File/separator) "appengine-java-sdk-" (get-sdk-version project)))

(defn start
  "Start the dev server."
  [project]
  (let [root-path (get-sdk-path project)
        address (get-in project [:jaq :address] "0.0.0.0")
        port (get-in project [:jaq :port] "3000")
        generated-dir (get-in project [:jaq :generated-dir] "data")
        default-bucket (get-in project [:jaq :default-gcs-bucket] "")
        war-app-path (get-in project [:jaq :war-app-path] "war")
        args [(str "--address=" address)
              (str "--port=" port)
              "--runtime=java8"
              (str "--generated_dir=" generated-dir)
              (str "--default_gcs_bucket=" default-bucket)
              war-app-path]]
    (println root-path args)
    (eval-in-project project
                     `(do
                        (java.lang.System/setProperty "appengine.sdk.root" (str ~@root-path))
                        @(future (com.google.appengine.tools.development.DevAppServerMain/main (into-array String [~@args])))))))

(defn explode
  "Explode the uberwar."
  [project & args]
  (let [war-path (war-file-path project (default-uberwar-name project))
        target-path (get-exploded-war-path project)]
    (println "Exploding" war-path "to" target-path)
    (unzip-file war-path target-path)
    (copy-file "war-resources/appengine-web.xml" (str target-path  "/WEB-INF/appengine-web.xml"))
    (copy-file "war-resources/queue.xml" (str target-path  "/WEB-INF/queue.xml"))))

(defn dev-server
  "Start a local dev server."
  [project & args]
  (println "dev server...")
  (start project))

(defn auth-token
  "Generate authorization tokens."
  [project & args]
  (let [path (get-in project [:jaq :credentials] ".credentials")]
    (auth/local-credentials path)))

(defn deploy
  "Deploys to App Engine."
  [project & args]
  (let [project-id (get-in project [:jaq :project-id])
        bucket (get-in project [:jaq :bucket])
        prefix (get-in project [:jaq :prefix])
        version (get-in project [:jaq :version])
        servlet (get-in project [:jaq :servlet] "servlet")]
    (println "Deploying app...")
    (loop [op (admin/deploy-app project-id bucket prefix version servlet)]
      (pprint op)
      (when-not (:done op)
        (sleep)
        (recur (admin/operation (:name op)))))))

(defn upload
  "Upload app to storage bucket."
  [project & args]
  (let [bucket (get-in project [:jaq :bucket])
        prefix (get-in project [:jaq :prefix])
        src-dir (get-exploded-war-path project)]
    (println "Uploading app [this may take a while]...")
    (storage/copy-local src-dir bucket prefix)))

(defn list-projects
  "List projects."
  [project & args]
  (let [projects (resource/projects)]
    (pprint projects)))

(defn list-locations
  "List available locations."
  [project & args]
  (let [project-id (get-in project [:jaq :project-id])
        locations (admin/locations project-id)]
    (pprint locations)))

(defn list-application
  "List application."
  [project & args]
  (let [project-id (get-in project [:jaq :project-id])
        app (admin/app project-id)]
    (pprint app)))

(defn create-project
  "Create project."
  [project & args]
  (let [project-id (get-in project [:jaq :project-id])
        project-name (get-in project [:jaq :project-name])]
    (loop [op (resource/create project-id project-name)]
      (pprint op)
      (when-not (:done op)
        (sleep)
        (recur (resource/operation (:name op)))))))

(defn create-application
  "Create application."
  [project & args]
  (let [project-id (get-in project [:jaq :project-id])
        location-id (get-in project [:jaq :location-id])]
    (loop [op (admin/create project-id location-id)]
      (pprint op)
      (when-not (:done op)
        (sleep)
        (recur (admin/operation (:name op)))))))

(defn migrate
  "Migrate traffic to application version."
  [project & args]
  (let [project-id (get-in project [:jaq :project-id])
        version (get-in project [:jaq :version])]
    (loop [op (admin/migrate project-id version)]
      (pprint op)
      (when-not (:done op)
        (sleep)
        (recur (admin/operation (:name op)))))))

(defn jaq
  "Manage a Google Appengine App."
  {:subtasks [#'unpack #'explode #'dev-server #'uberwar
              #'auth-token #'deploy #'upload #'create-project
              #'create-application #'list-projects #'list-locations
              #'list-application #'migrate]}
  ([project]
   (help-for project "jaq"))
  ([project subtask & args]
   (case subtask
     "unpack" (apply unpack project args)
     "explode" (apply explode project args)
     "dev-server" (apply dev-server project args)
     "auth-token" (apply auth-token project args)
     "deploy" (apply deploy project args)
     "upload" (apply upload project args)
     "migrate" (apply migrate project args)
     "create-project" (apply create-project project args)
     "create-application" (apply create-application project args)
     "list-projects" (apply list-projects project args)
     "list-locations" (apply list-locations project args)
     "list-application" (apply list-application project args)
     "uberwar" (apply uberwar project args))))
