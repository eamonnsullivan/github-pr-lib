# github-pr-lib

A small, very simple library for opening, closing, approving and commenting on pull requests on Github. It uses Github's v4 GraphQL API.

## Usage

### Create a new pull request
```
(def token (System/getenv "GITHUB_ACCESS_TOKEN"))
(def options {:repo "my-org/my-repo"
              :title "A title for the pull request"
              :body "The body of the pull request"
              :base-branch "main-branch-name"
              :head-branch "your-branch-name"
              :draft true
              :maintainerCanModify true})
(def pullrequest (createpr token options))
```
### Modify a pull request
```
(modifypr token (update pullrequest :isDraft not)) ;; toggle the "draft" property
```
### Mark a pull request ready for a review
```
(ready-for-review token pullrequest)
```
### Comment on a pull request
```
(commentpr token pullrequest "A review comment")
```
### Close a pull request
```
(closepr token pullrequest "Optional closing comment")
```
### Merge a pull request
```
(def result (mergepr token pullrequest))
```

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
