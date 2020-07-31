# Purpose

**Type of PR**
* [ ] Bug Fix
* [ ] Feature Work
* [ ] Revert
* [ ] Refactor / Code Cleanup
* [ ] Other (fill in...)

**Review Urgency**
* [ ] ASAP
* [ ] Next 2 hours
* [ ] End of Day
* [ ] Tomorrow
* [ ] End of Week
* [ ] Other (fill in...)


## Short Description
<!-- Feature / Problem overview -->


## Changelog / Code Overview
<!-- What was changed / added / removed and why. If you changed the frontend please include screenshots -->


## Test Plan
<!-- How was this code tested / How should reviewers test it? -->


## Deploy Plan
<!--
How will this be deployed? Are there any special cases you need to think about?
How will this change be reverted if there is an issue?
-->


## What to look for
<!-- Call out what each team/reviewer should look for and a rough time estimate. -->


## Additional Details
<!-- Any relevant links to TP / Sentry / RFC -->


## Checklist of Common Gotchas
<!-- Checklist of things to make sure you do before deploying add to this as much as you want-->

* Was a task created?
  * [ ] Create a new queue or choose where to route it
* Did a task signature change?
  * [ ] Make sure you handle both the old and new signatures
* Schema Change?
  * [ ] Make sure you migrate on 1.8
* New Local settings value?
  * [ ] Make sure you have the value added to Zookeeper
* New Buildout dependency
  * [ ] Buildout on deploy
* Compile Assets if old LESS or JS is touched and remove source maps
* Requires new running service on locals (i.e. kafka/microservice/whatever)?
  * [ ] Does it include instructions on how to work around for local?
