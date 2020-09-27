# github-pr-lib

A small, very simple library for opening, closing, approving and commenting on pull requests on Github. It uses Github's v4 GraphQL API.

## Usage

### Create a new pull request
```
(def token (System/getenv "GITHUB_ACCESS_TOKEN"))
(def options {:repo "my-org/my-repo"
              :title "A title for the pull request"
              :body "The body of the pull request"
              :base "main-branch-name"
              :branch "your-branch-name"
              :draft false
              :maintainerCanModify true})
(def pullrequest-id (create-pull-request token options))
```
The `draft` option defaults to false, while the `maintainerCanModify` option defaults to true.

### Toggle a pull request's draft status
TBD
### Change a pull request title
TBD
### Mark a pull request ready for a review
TBD
### Comment on a pull request
TBD
### Close a pull request
TBD
### Merge a pull request

Run the project's tests (they'll fail until you edit them):

    $ clojure -A:test:runner -M:runner

Build a deployable jar of this library:

    $ clojure -A:jar -M:jar

Install it locally:

    $ clojure -A:install -M:install

Deploy it to Clojars -- needs `CLOJARS_USERNAME` and `CLOJARS_PASSWORD` environment variables:

    $ clojure -A:deploy -M:deploy

## License

Copyright © 2020 Eamonn

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
