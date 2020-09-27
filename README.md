# github-pr-lib

A small, very simple library for opening, closing, approving and commenting on pull requests on Github. It uses Github's v4 GraphQL API.

## Usage

### Create a new pull request
```
(def token (System/getenv "GITHUB_ACCESS_TOKEN"))
(def options {:owner "eamonnsullivan"
              :name "github-pr-lib"
              :title "A title for the pull request"
              :body "The body of the pull request"
              :base "main-branch-name"
              :branch "your-branch-name"
              :draft true
              :maintainerCanModify true})
(def pullrequest-id (create-pull-request token options))
```
The `draft` and `maintainerCanModify` options default to true.

### Update a pull request
```
(def updated {:title "A new title"
              :body "A new body"
              :maintainerCanModify false})
(update-pull-request token "https://github.com/eamonnsullivan/github-pr-lib/pull/3" updated)
```
### Mark a pull request as ready for review
```
(mark-ready-for-review token "https://github.com/eamonnsullivan/github-pr-lib/pull/3")
```
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
