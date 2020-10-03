# github-pr-lib

A small, very simple library for opening, closing, approving and commenting on pull requests on Github. It uses Github's v4 GraphQL API.

## Usage

You will need a Github access token with `repo` permissions. This is one way to provide that value:
```
(def token (System/getenv "GITHUB_ACCESS_TOKEN"))
```

All of these methods return the permalink of the new or updated pull request or comment, e.g., `https://github.com/eamonnsullivan/github-pr-lib/pulls/1`.

### Create a new pull request
```
(def options {:title "A title for the pull request"
              :body "The body of the pull request"
              :base "main or master, usually"
              :branch "the name of the branch you want to merge"
              :draft true
              :maintainerCanModify true})
(def new-pr-url (create-pull-request token "https://github.com/eamonnsullivan/github-pr-lib" options))
```
The `title`, `base` and `branch` are mandatory. You can omit the `body`, and the `draft` and `maintainerCanModify` options default to true.

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
Only handles issue comments on pull requests at the moment.
```
;; returns the permalink for the comment
(def comment-link (add-pull-request-comment token new-pr-url "Another comment."))
```
### Edit an issue comment
```
(edit-pull-request-comment token comment-link "The new body for the comment.")
```
### Close a pull request
```
(close-pull-request token new-pr-url)
```
### Reopen a pull request
```
(reopen-pull-request token new-pr-url)
```
### Merge a pull request
```
;; All of these fields are optional. The merge-method will default to "SQUASH".
;; The merge will fail if the pull-request's URL can't be found, if the pull
;; request's head reference is out-of-date or if there are conflicts.
(def merge-options {:title "A title or headline for the commit."
                    :body "The commit message body."
                    :mergeMethod "MERGE" or "REBASE" or "SQUASH"
                    :authorEmail "someone@somwhere.com"})
(merge-pull-request token new-pr-url merge-options)
```
## Development Notes

To run the project's tests:

    $ clojure -A:test:runner -M:runner

To build a deployable jar of this library:

    $ clojure -Spom               # to update any dependencies
    $ clojure -A:jar -M:jar

To install the library locally:

    $ clojure -A:install -M:install

To deploy it to Clojars -- needs `CLOJARS_USERNAME` and `CLOJARS_PASSWORD` environment variables or Maven settings:

    $ clojure -A:deploy -M:deploy

## License

Copyright © 2020 Eamonn

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
