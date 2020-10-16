# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [0.0.17]-2020-10-16
- Remove some redundancy from the GraphQL, and rearranged them (alphabetically) to make it easier to spot these.
- Remove references to maintainerCanModify. It seems like we can't change this. Mutations have no affect and it is always false.
## [0.0.16]-2020-10-15
- Fixes docstrings for some methods
- Tweaks to CI workflow

## [0.0.12]-2020-10-06
- Return more information about the comment/pull request
- Fix authentication for the GET request (to get ids).
  These will work on private repos now too.

## [0.0.11]-2020-10-04
- Refactor getting node ids to avoid paging requests
- Expose more properties about pull requests

## [0.0.9]-2020-10-03
-fix error in build.

## [0.0.6]-2020-10-03
- Implement editing of issue comments

## [0.0.5]-2020-10-02
- Use a 'main' branch, pull-request checks

## [0.0.4]-2020-10-02
Very few user-visible changes:
- Documentation, adds change log.
- Github actions to auto-deploy on merge

## [0.0.3]-2020-10-02
- Initial implementation of merging

## [0.0.2]-2020-10-01
- Allow a repo URL to be supplied to create-pull-request

## [0.0.1]-2020-10-01
- Initial version
