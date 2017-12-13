# JAQ Lein

A Lein Plugin designed to make using Google App Engine usage idiomatic.

## Installation

Available on Clojars:

```
[com.alpeware/lein-jaq "0.1.0-SNAPSHOT"]
```

## Status

Alpha quality with some API changes expected.

## Tasks 

```
Manage a Google Appengine App.

Subtasks available:
create-application   Create application.
deploy               Deploys to App Engine.
dev-server           Start a local dev server.
migrate              Migrate traffic to application version.
create-project       Create project.
uberwar              Create a $PROJECT-$VERSION.war with dependencies.
upload               Upload app to storage bucket.
list-locations       List available locations.
list-projects        List projects.
list-application     List application.
explode              Explode the uberwar.
auth-token           Generate authorization tokens.
unpack               Unpack the App Engine SDK.

Run `lein help jaq $SUBTASK` for subtask details.

Arguments: ([] [subtask & args])
```

## Configuration

Add the following map to your project.clj:

```
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
        :version "v1"}
```


## License

Copyright © 2017 Alpeware, LLC.

Distributed under the Eclipse Public License, the same as Clojure.
