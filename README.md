# Takeout

Takeout is the Android port of the Planet web application. It will work with Planet like Take Home works with BeLL.

### App Screenshots:

<p align="middle">
<img src="https://user-images.githubusercontent.com/37878201/43286563-67eaf1f0-90f0-11e8-9bb6-bec370bca3c9.png" width="267" height="427">
<img src="https://user-images.githubusercontent.com/37878201/43286561-67c05706-90f0-11e8-9f52-f9de73f94df1.png" width="267" height="427"> 
<img src="https://user-images.githubusercontent.com/37878201/43286562-67d32638-90f0-11e8-98f6-a10ca2c5650b.png" width="267" height="427"> 
</p>


### Getting started for Users
 
On your Android device, go to [Releases](https://github.com/ole-vi/takeout/releases/tag/latest) and download the latest version of the APK of the Application. Click on the downloaded APK and choose `install`.

### Getting started for Interns

Virtual Interns who will be working on the project should start out by setting up the repository on their own device in Android Studio. The steps are very similar to the process followed for cloning the [open-learning-exchange](https://github.com/open-learning-exchange/open-learning-exchange.github.io) repository. The only difference is that you **do not need to fork** the takeout repository as you are now a part of the team. 

Open the takeout repository on Android Studio. Click on `Build` to sync and build the project. If you face any issues in syncing or compiling the project, use the [mobile gitter channel](https://gitter.im/open-learning-exchange/mobile) or the Mobile Hacking Hangout to discuss and solve your difficulties with the team.

> We also encourage you add the solutions to any syncing or compiling issues to this README document in the Troubleshooting section as a guidance to the future virtual interns.

#### Setting up the Android device for Testing

As the Takeout App will mostly be used on 10-inch OLE Tablets, the best way to run and test the application will be on the 10-inch tablet emulator (unless you have an actual 10-inch tablet):

1. If you have completed the First Steps, then you have already enabled virtualization on your device.
2. After opening and syncing the takeout project in Android Studio, click on `Run`. 
3. At the bottom of the dialog, click on `Add New Virtual Device`.
4. Choose `Tablet` and then choose the `10.1" WXGA Tablet` (1280 x 800 mdpi).
5. Choose API level according to the latest release (25 or greater) and continue.
6. Verify the configuration and click finish.

Your device should configure and the application will run on the virtual device.

##### Tools

Along with the 10-inch tablet, you can also use an actual device to run and test the application - it is always good to know how the App functions on different screen-sizes and densities. 

[Vysor](https://www.vysor.io/) is software that helps display your Android screen into your computer. It helps you explain the issue more in detail. Plus, everybody in the team can see what is happening on your screen, therefore we can help each other in debugging.

##### Creating Issues and Pull Requests

The process for creating issues and pull requests is very similar to First Steps. Remember to branch off of the master branch before working on and creating a new pull request.

##### Including the correct format for the code

To maintain uniform formatting of the code, use the following keys combinations in .java, .xml, or any other files in Android Studio before committing and pushing the code to the takeout repository:
- Windows: `shift + ctrl + alt + L`
- Linux: `ctrl + alt + super + L` (super is the Windows icon key next to the alt key)
- Mac: `ctrl + command + shift + L`

Remember, this is an important step to remember so that we are able to maintain a high standard of code.

### Troubleshooting

Some of the issues that you can come across may be related to the obsolete commands, gradle building, the version of Android Studio your are using, etc. Add your problem and the solution to your problem in this section to help future interns. 
