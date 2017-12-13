(def sdk-version "1.9.59")

(defproject com.alpeware/lein-jaq "0.1.0-SNAPSHOT"
  :description "JAQ - Bringing Clojure to Google App Engine"
  :url "http://www.alpeware.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :local-repo ".m2"
  :dependencies [[org.clojure/clojure "1.9.0"]

                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/tools.logging "0.4.0"]

                 [com.alpeware/jaq-services "0.1.0-SNAPSHOT"]

                 [lein-ring "0.12.2"]
                 [net.lingala.zip4j/zip4j "1.3.2"]]

  ;; available configuration in your project.clj
  :jaq {:sdk-path "sdk"
        :sdk-version ~sdk-version
        :war-app-path "war"
        :generated-dir "data"
        :default-gcs-bucket "staging.alpeware-foo-bar.appspot.com"
        :address "0.0.0.0"
        :port 3000
        :project-id "project-id"
        :project-name "project name"
        :location-id "europe-west3"
        :bucket "staging.project-id.appspot.com"
        :prefix "apps/latest"
        :version "v1"})
