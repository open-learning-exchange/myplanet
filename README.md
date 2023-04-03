# myPlanet

myPlanet is the Android port of the [Planet web application](https://github.com/open-learning-exchange/planet). It will work with Planet community / nation.

### App Screenshots:

<p align="middle">
<img width="757" alt="Screenshot 2019-09-06 at 8 47 46 AM" src="https://user-images.githubusercontent.com/2836302/64419353-439ef300-d08c-11e9-8e78-40d086b251df.png">
<img width="755" alt="Screenshot 2019-09-06 at 8 48 11 AM" src="https://user-images.githubusercontent.com/2836302/64419358-46014d00-d08c-11e9-9c7c-dfb28ca2ef26.png">
<img width="763" alt="Screenshot 2019-09-06 at 9 02 11 AM" src="https://user-images.githubusercontent.com/2836302/64419366-4d285b00-d08c-11e9-9770-740ce5ad278b.png">
<img width="771" alt="Screenshot 2019-09-06 at 9 53 43 AM" src="https://user-images.githubusercontent.com/2836302/64419378-53b6d280-d08c-11e9-86cb-53814e832795.png">
</p>


### Getting started for Users
 
On your Android device, go to [Releases](https://github.com/open-learning-exchange/myplanet/releases) and download the latest version of the APK of the Application. Click on the downloaded APK and choose `install`.

### Getting started for Interns

Virtual Interns who will be working on the project should start out by setting up the repository on their own device in Android Studio. The steps are very similar to the process followed for cloning the [open-learning-exchange](https://github.com/open-learning-exchange/open-learning-exchange.github.io) repository. The only difference is that you **do not need to fork** the myPlanet repository as you are now a part of the team. 

Open the myPlanet repository on Android Studio. Click on `Build` to sync and build the project. If you face any issues in syncing or compiling the project, use the [mobile gitter channel](https://gitter.im/open-learning-exchange/mobile) or the Mobile Hacking Hangout to discuss and solve your difficulties with the team.

> We also encourage you add the solutions to any syncing or compiling issues to this README document in the Troubleshooting section as a guidance to the future virtual interns.

#### Setting up the Android device for Development and Testing

As the myPlanet App will mostly be used on 10-inch OLE Tablets, the best way to run and test the application will be on the 10-inch tablet emulator (unless you have an actual 10-inch tablet):

1. If you have completed the First Steps, then you have already enabled virtualization on your device.
2. After opening the myPlanet project in Android Studio, sync it by going to `file/sync project with gradle files`.

<img width="507" alt="Screen Shot 2019-04-11 at 11 04 15 PM" src="https://user-images.githubusercontent.com/29234807/56009589-4c8ec280-5cae-11e9-9907-908e61c5a8c6.png">

3. Once syncing is over, click on `Run`.
4. At the bottom of the dialog, click on `Add New Virtual Device`.
5. Choose `Tablet` and then choose the `10.1" WXGA Tablet` (1280 x 800 mdpi).
6. Choose API level according to the latest release (25 or greater) and continue.
7. Verify the configuration and click finish.

Your device should configure and the application will run on the virtual device.

#### Logging in to myPlanet

Now the app needs to sync with the server to be able to log in. Click on the settings icon on the top right and fill in the following.

1. **Planet Server Address**: If you are on an emulator and running planet on your machine your planet server address will most probably be **10.0.2.2:3100**. If your instance of planet is running on a raspberry pi your server address would be raspberry-pi-ip-address:3100. The planet instance might not always run on port 3100, so double check the ip address and port your instance of planet is running on.
 
2. **Server Pin**: Login to Planet with the admin account(the one that was created in the first steps), go to manager settings on the top right and find table pin/server pin there.

<img width="1920" alt="Screen Shot 2020-08-23 at 4 47 21 PM" src="https://user-images.githubusercontent.com/6264813/90988484-14b1b380-e561-11ea-8f9e-979fdc8e5be4.png">

Enter the details and press sync.

![Screenshot_1597629265](https://user-images.githubusercontent.com/6264813/90350501-5c898580-e00b-11ea-8cfe-07ace2907b62.png)

Finally you need to create a **new account for development and testing** either on the myPlanet app or on Planet after connecting to the server since at this moment admin login is not supported.

##### Tools

Along with the 10-inch tablet, you can also use an actual device to run and test the application - it is always good to know how the App functions on different screen-sizes and densities. 

[Vysor](https://www.vysor.io/) is software that helps display your Android screen into your computer. It helps you explain the issue more in detail. Plus, everybody in the team can see what is happening on your screen, therefore we can help each other in debugging.

##### Creating Issues and Pull Requests

The process for creating issues and pull requests is very similar to First Steps. Remember to branch off of the master branch before working on and creating a new pull request.

#### How to start contributing

If you catch a bug, have a feature request or just see something that could be improved, **create a new issue** and let others know about it so that it can be reviewed. If its apporved you can assign yourself to the issue and start working on it. You don't have to work on every issue you create so feel free to open issues on things you do not understand yet.

Before you start working on any issue make sure that the issue is valid and approved by the members of the team and always assign yourself to the issue so that nobody else works on the same issue.

Since you are just staring out it is important to get some understanding of the codebase and it's structure. A good way to understand the codebase while contributing is to start with simple issues like changing color of a button or fixing the spacing of some element. If you feel more confident you can work on more challenging issues.

Another excellent way to contribute is by adding comments. While going through the codebase if you don't understand a piece of code or feel like a comment could help make the purpose of the code clearer add a comment so that interns that join after you have an easier time understanding the codebase.

Finally don't hesitate to ask for help. You are encouraged to find a solution yourself but if you unable to find one or need a second opinion feel free to reach out. 

##### Including the correct format for the code

To maintain uniform formatting of the code, use the following keys combinations in .java, .xml, or any other project files you have changed in Android Studio before committing and pushing the code to the myPlanet repository:

For Windows, use `ctrl + alt + L` to format few lines and `shift + ctrl + alt + L` to format an entire file.

For Linux use `ctrl + alt + super + L`. (super is the Windows icon key next to the alt key)

For Mac: `ctrl + command + shift + L`.

![mac](https://user-images.githubusercontent.com/37878201/43735982-1d0cf1c2-998a-11e8-8edb-5bfea2522a35.png)

Remember, this is an important step to remember so that we are able to maintain a high standard of code.

### Troubleshooting

Some of the issues that you can come across may be related to the obsolete commands, gradle building, the version of Android Studio your are using, etc. Add your problem and the solution to your problem in this section to help future interns. 
