


# DevOps-Project
## Setup
First, clone this repository.
Navigate inside */web_srv/* , do `Vagrant up` .
Navigate inside */ansible_srv*, open the `Vagrantfile` and do the following :

 - Set `<Your local ansible_srv path>` to the path of this ansible_srv folder on your local.
 - Set `<Your private_key path>` to the path of the private_key on your local which will be required to connect to web_srv. If you are setting up *web_srv* using vagrant, this will usually be `<Your path till web_srv/web_srv/.vagrant/machines/default/virtualbox>` .
 - Make sure this private key is named as `private_key` .

Run `Vagrant Up` and `Vagrant ssh` .

Basically now the VM formed through *ansible_srv* will be the **ansible server**, and will contain all the ansible playbooks. Inside the VM, do `cd /ansible_srv`

Run `ansible-playbook ssh-playbook.yml`

The VM formed through *web_srv* will be the **jenkins server**. All the ansible playbooks will make changes on this machine and it will set up the jenkins server, configure checkboxio and iTrust builds, and contain the git hooks.

## Jenkins setup and automation

Run `ansible-playbook -i inventory setup-jenkins.yml` .

The *setup-jenkins.yml* internally will call the following :

 - *install-jenkins.yml* : This calls a role - *jenkins*. This role has been defined in `/roles/jenkins/tasks/main.yml`. This role will install Java JDK 8, import jenkins key, add jenkins repository to apt and install jenkins accordingly.
 - *configure-jenkins.yml* : This role is responsible for configuring jenkins. It will add a new user using admin credentials as specified in the script. You can change var `username` and var `user_pwd` to set your username and password on the newly created user. It internally uses the admin password which is in `/var/lib/jenkins/secrets/initialAdminPassword` and is by default generated by jenkins.

You can test this by going to [http://192.168.33.100:8080/](http://192.168.33.100:8080/) . 
You should see the jenkins web ui come up. You can login using the admin credentials or your newly created user.
## Automatically setup build jobs
We are joing to use **jenkins-job-builder** inorder to build jobs for **checkbox.io** and **iTrust**
### Checkbox.io
Run the playbook *install-jjb.yml*
This playbook contains - 
Installation of **nodejs, npm and Jenkins Job builder**.
Inorder to build the job, you need to give the credentials in *jenkins_jobs.ini* file so that you get the access to create the build job on jenkins.

There is a jenkins job file placed inside `/jobs` directory - *checkbox-build.yml* which builds the checkbox.io repo by running `npm test` in the *checkbox.io-build-workspace/server-side/site/* directory.

And later the playbook runs `jenkins-jobs update jobs` command to create the build for the job files inside `/jobs` directory.

After running this playbook, go to the browser `http://192.168.33.100:8080` and you can see the build job created once you login - 

    ** Screenshot **
Click on **`Build now`** and you can see the Build success results - 

    ** Screenshot **

### iTrust

In order to run iTrust on our system, we need to initialize our environment with all dependencies requried by iTrust. We need to install: 
 - Java SE 8 or 10
 - MySQL 

We also require to install the following dependencies:
 - Maven (to execute the build commands)
 - Git (to clone the repositories)
 - Ansible (to run the playbook that will update the template files in iTrust)

We will install the above dependencies using an ansible playbook that automates the entire process. To do this, we have to run the playbook `setup-build-iTrust.yml` located inside the `/ansible_srv/helper-playbooks` directory. We run the following commands using this playbook and the inventory file which contains the host we want to run these plays(task) on:

```
cd /ansible_srv/
ansible-playbook helper-playbooks/setup-build-iTrust.yml -i inventory
```

This playbook will first ensure Java is installed on the system. It will then install MySQL and create the root user and password. Next it will install the other dependencies required and finally it will edit the `/etc/sudoers` file to ensure jenkins has root privileges to run the build commands. We then create a jenkins job on our jenkins server using the Jenkins Job Builder file `iTrust-build.yml` located inside the `/ansible_srv/jobs` folder. We provide the job with a name and a git url to clone. Next we include the shell commands that should be run inorder to successfully build iTrust.

Shell commands in JJB file :
 - **ansible-playbook /jenkins-srv-files/setup-iTrust-repo.yml -i /jenkins-srv-files/inventory** :
   - This command will run an ansible playbook on the machine itself (localhost) that will create the **db.properties** and **mail.properties** file with the correct credentials to the MySQL database and email account (for testing smtp access).
 - **cd /var/lib/jenkins/workspace/iTrust/iTrust2**
   - This command moves into the required workspace to build iTrust
 - **sudo mvn -f pom-data.xml process-test-classes**
   - This command will create the necessary data and tables for the tests.
 - **sudo mvn clean test verify checkstyle:checkstyle -Djetty.port=9999**
   - This command will start the server on port 9999, run the required tests and finally bring the server back down.

After running this playbook, go to the browser `http://192.168.33.100:8080` and you can see the build job created once you login: 

Click on **`Build now`** and you can see the Build success results - 

## npm test for Checkbox.io 

We have succeffully built checkbox.io, but now we need to check that the server is actually running. In order to do that, we will first need to set up our server. To do this we will run the playbook `setup-checkbox-env.yml` located inside the `/ansible_srv/helper-playbooks` directory. We run the following commands using this playbook and the inventory file which contains the host we want to run these plays(task) on:

```
cd /ansible_srv
ansible-playbook helper-playbooks/setup-checkboxio.yml -i inventory
```
This playbook will first set up the required environment variables required for our server to communicate with our database. Next we will install and configure nginx server. Lastly, we will install mongodb on the target host and add the admin user to the database. 

**A good practice would be refreshing your terminal (logging out and back in) to ensure the environmental variables are successfully set**

After running this playbook,we can go to the browser `http://192.168.33.100:80` and you can see the checkbox.io home page 

The test script responsible for testing the server is test_server.js located under the **../test** directory.
The script has 2 tests:
 - One checks for static webpage using mocha, chai and got modules.
 - The other test checks for the api using "supertest" module (supertest module is high-level abstraction for testing HTTP).


## Git hook to trigger a build
Command: `ansible-playbook -i inventory git-hook-playbook.yml`

We have written a playbook `git-hook-playbook.yml` which has all the tasks to create a hook for the iTrust and checkboxio build jobs. This playbook will create the git bare-repositories for the two projects. It links the cloned repositories of the projects to these bare-repositories such that the cloned repo identifies the bare repo as a remote repo. The bare repository has a `post-receive` hook which will be triggered when a person commits something in the project and pushes it using the command `git push jenkins master`.  The cloned projects of iTrust and checkbox.io are in the root directory. You can access this by doing `cd /checkboxio`. Make modification to any file and then follow the steps :

 - `git add -A`
 - `git commit -m "test git hook"`
 - `git push jenkins master`

The post-receive hook contains a curl command which triggers the job linked with the git repo in the `url=` parameter.

You should be able to view the automatically triggered build in your [jenkins](http://192.168.33.100:8080/) by logging in using the *admin* user.

