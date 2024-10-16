# klaviyo-android-test-app
Internal tester application for [Klaviyo Android SDK](https://github.com/klaviyo/klaviyo-android-sdk).
This app is not intended for public release or demonstration. It is used to test SDK changes and features
before they are released to the public.

## Development
Download Android studio and clone this repo. Once gradle resolves all the dependencies you 
should be good to go.

To compile against a local version of the SDK, clone the SDK repo into the same parent directory as
this one. Set the `allowComposite` flag to true in the app's `settings.gradle` file. 
As you are working on SDK changes, they will be detected and recompiled when you build this app.

> Note: the refresh versions plugin doesn't get along well with composite builds. It will read all versions
> from one `versions.properties` file. You may have to adjust some version numbers for the first gradle sync. 

We maintain [setup instructions](https://klaviyo.atlassian.net/wiki/spaces/EN/pages/3601793078/Android+Development+Setup) 
and [release process](https://klaviyo.atlassian.net/wiki/spaces/EN/pages/3761734100/Android+Test+App+Release+Guide)
documentation in the wiki.
