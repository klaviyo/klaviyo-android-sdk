## Test app for Klaviyo Android SDK
Most of the Android SDK project is going to live in another repository but there is an internal-only
testing application that we want to develop to help engineers and project managers test releases of 
the SDK. Since this project is internal it will need to live separately to the rest of the SDK 
project and therefore needs its own repository.

### Development
Download Android studio and clone this repo. Once the gradle resolves all the dependencies you 
should be good to go.

If you are also working on SDK changes, I've set up this project as composite build that includes
the SDK repo if it is detected in the same parent directory. That means any changes you make to 
the SDK should be detected and recompiled. We no longer need to assemble artifacts or publish to 
maven local every time you change SDK code. Refer to the mobile push team's wiki (confluence) for a
more complete guide on getting set up with android development on your local.

### Release Process
We use Google Play Console to distribute the app to internal Klaviyo testers on the "Internal Track". 
You can build a signed release bundle on your local machine (signing keys are stored in 1Password) 
and upload it to the Play Console in a browser. If you are not already, inquire with mobile push team
to be added to the developer account. The owner of the account is google.developer@klaviyo.com, an
account created by IT for this express purpose. More detail on Play Console can also be found in the
Mobile Push team's wiki.

I am testing a Github action to automatically generate, sign and upload a release build triggered
by creating a release from the github repo UI. 
