# github-pr-lib

A small, very simple library for opening, closing, approving and commenting on pull requests on Github. It uses Github's v4 GraphQL API.

## Usage

```
(def token (System/getenv "GITHUB_ACCESS_TOKEN"))
```

### Create a new pull request
```
(def options {:owner "eamonnsullivan"
              :name "github-pr-lib"
              :title "A title for the pull request"
              :body "The body of the pull request"
              :base "main-branch-name"
              :branch "your-branch-name"
              :draft true
              :maintainerCanModify true})
(def new-pr-url (create-pull-request token options))
```
The `draft` and `maintainerCanModify` options default to true.

### Update a pull request
```
(def updated {:title "A new title"
              :body "A new body"
              :maintainerCanModify false})
(update-pull-request token new-pr-url updated)
```
### Mark a pull request as ready for review
```
(mark-ready-for-review token new-pr-url)
```
### Comment on a pull request
```
(add-pull-request-comment token new-pr-url "Another comment.")
```
### Close a pull request
```
(close-pull-request token new-pr-url)
```
### Reopen a pull request
```
(reopen-pull-request token new-pr-url)
```

Run the project's tests:

    $ clojure -A:test:runner -M:runner

Build a deployable jar of this library:

    $ clojure -S:pom               # to update any dependencies
    $ clojure -A:jar -M:jar

Install it locally:

    $ clojure -A:install -M:install

Deploy it to Clojars -- needs `CLOJARS_USERNAME` and `CLOJARS_PASSWORD` environment variables:

    $ clojure -A:deploy -M:deploy

## License

Copyright © 2020 Eamonn

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
