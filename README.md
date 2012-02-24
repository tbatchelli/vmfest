# vmfest

VMFest is a clojure wrapper to (currently) [VirtualBox] [vbox], a virtualization platform that is pretty good with virtualizing servers. 

Although VMFest provides tool-like functionality, VMFest is also a library that you can use to create your own virtualization environment.

[vbox]: http://www.virtualbox.org 

With VMFest you can easily create and operate virtual machines, with emphasis on creating many clones of the same model VMs. 

## Models and Instances

In VMFest, a *model* is a VM image. Although it is possible to run an actual VM off of this image in VMFest, this is not the common usage scenario. The common usage scenario is to run an *instance* of the model. 

An instance is a transient VM that will be created dynamically from the model and destroyed after being used. You can start as many instances as you need from the same model. Each instance created is virtually a clone of the model, but the way VirtualBox works makes this cloning a space efficient operation, namely, each clone takes significant less disk space than the model image.

It is possible to use VMFest in a more standard way, in which each VM uses a permanent image. A permanent image is an image that keeps its changes beyond the lifetime of the VM that runs on it. 

## Prerequisites to use VMFest

To use VMFest you first need to install [VirtualBox 4.0x][vbox] in
your machine.  (NOTE: Virtualbox 4.1.x and above will not work for now)

VMFest uses a web service that VirtualBox installs. This service is authenticated and there are many ways to authenticate, but for simplicity you can disable authentication by running the following in your shell:

```
$ VBoxManage setproperty websrvauthlibrary null
```

## Usage ( vmfest v 0.2.3)

### Installing a Model Image

Create a new [Leiningen](https://github.com/technomancy/leiningen) project.

```
$ lein new vmfest-quickstart
$ cd vmfest-quickstart
```

Edit the ```project.clj``` so it looks like: 

```clojure
(defproject vmfest-quickstart "1.0.0-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [vmfest              "0.2.3"]])
```

Get the dependencies: 
```
$ lein deps
```

Start your favorite REPL, for example: 
```
$ lein repl
```

Before we can instantiate an image, we need to install it as model image. To make things easy I have published a model image (more to come). The following will download an install an ubuntu 10.10 64-bit model image.

```clojure
(use 'vmfest.manager)
(use 'vmfest.virtualbox.image)

;; crate a connection to the local VirtualBox server
(def my-server (server "http://localhost:18083"))

;; download and install a model
(setup-model "https://s3.amazonaws.com/vmfest-images/ubuntu-10-10-64bit-server.vdi.gz" my-server)
```

### Creating and Running Images from a Model Image
Once the model image is installed, you can create an instance off of it.

```clojure
(update-models) ;; this will pick up all defined models.
(def my-machine (instance my-server "bacug-machine" :vmfest-ubuntu-10-10-64bit-server :micro))
```

At this point, you can operate your instance.

```clojure
(start my-machine) ;; starts your vm instance
(pause my-machine) ;; pauses your running instance
(resume my-machine) ;; resumes your paused instance
(power-down my-machine) ;; turns off your machine. The changes in the filesystem will be lost
(destroy-my machine) ;; removes any trace of this instance having ever existed
```

Now let's say you want to start 10 instances of the same model.

```clojure
;; find some names for those instances
(def clone-names #{"c1" "c2" "c3" "c4" "c5" "c6"})

;; create the instances
(def my-machines (map #(instance my-server % :ubuntu-10-10-64bit :micro)))

;; start them all!
(map start my-machines)

;; stop them all
(map power-down my-machines)

;; remove them all
(map destroy my-machines)
```

### More stuff you can do ... (old documentation, still works for v0.2.3)

``` clojure
;; load vmfest 
(use 'vmfest.manager)

;; define a connection to the vbox server. It doesn't create any socket
(def my-server (server "http://localhost:18083"))

;; see what images are available
(hard-disks my-server)

;; get all the data about the registered images
(pprint (map as-map (hard-disks my-server)))

;; see what guest OSs are supported by vbox
(guest-os-types my-server)

;; create a new VM ...
(def my-machine (create-machine my-server "bacug-machine" "Ubuntu_64" basic-config
                                "/Users/tbatchelli/VBOX-HDS/Ubuntu-10-10-64bit.vdi"))

;; ... start it ...
(start my-machine)

;; ... pause it ...
(pause my-machine)

;; ... resume it ...
(resume my-machine)

;; ... send a shut down key ... (won't work on this ubuntu)
(stop my-machine)

;; ... turn the machine off ...
(power-down my-machine)

;; ... aaaand, get rid of it.
(destroy my-machine)

;; Let's use this in a functional way:
;; 1) define the machine again
(def my-machine (create-machine my-server "bacug-machine" "Ubuntu_64" basic-config
                                "/Users/tbatchelli/VBOX-HDS/Ubuntu-10-10-64bit.vdi"))

;; 2) Define the names of the machines
(def clone-names #{"c1" "c2" "c3" "c4" "c5" "c6"})

;; 3) Create a bunch a seq of machines same image based on the names defined
(def my-machines
  (map #(create-machine my-server % "Ubuntu_64" basic-config
                        "/Users/tbatchelli/VBOX-HDS/Ubuntu-10-10-64bit.vdi")
       clone-names))

;; 4) Start them all
(map start my-machines)

;; 5) Power them all down
(map power-down my-machines)

;; 6) clean up
(map destroy my-machines)


;; Now, this was pretty low level. Let's build some infrastructure on
;; top of it. This infrastructure defines *machines* and *images* that
;; can be used to instantiate machines
(def my-machine (instance my-server "bacug-machine" :ubuntu-10-10-64bit :micro))
(def my-machine-2 (instance my-server "bacug-machine-2" :cent-os-5-5 :micro))

;; now we can use the same operations as before
(start my-machine)
(start my-machine-2)
(power-down my-machine)
(power-down my-machine-2)
(destroy my-machine)
(destroy my-machine-2)

;; View currently defined machines
(pprint (map as-map (machines my-server)))

;; you can search machines too
(def my-test-machine (find-machine my-server "Ubuntu-10-10-64bit-Immutable")))

(start my-test-machine)

;; now, what ip did this machine get?
(get-ip my-test-machine)

;; ssh into it
;; $ ssh user@<ip>

;; kill it again
(power-down my-test-machine)
(destroy my-test-machine)
```

# Instructions to setup vmfest v0.2.2 with [pallet 0.4.x](https://github.com/pallet/pallet "pallet")

NOTE: This process has been greatly simplified with VMFest v0.2.3 and Pallet v0.6.2+. The setup instructions will come soon (I promise).

1. Install VirtualBox on your machine
2. Disable login credential: 

    ``` 
    $ VBoxManage setproperty websrvauthlibrary null
    ```

3. Download and uncompress the following [image](https://s3.amazonaws.com/vmfest-images/ubuntu-10-10-64bit-server.vdi.gz "image")
4. Clone the image to its final destination (```~/vmfest/models```):

    ``` 
    $ mkdir -p ~/vmfest/models
    $ VBoxManage clonehd /path/to/downloaded/ubuntu-10-10-64bit-server.vdi ~/vmfest/models/ubuntu-10-10-64bit-server.vdi
    ```

    * This should produce a uuid for your new image. Keep it around
5. Start VirtualBox (the GUI) and:
    1. Create an new image Linux - ```Ubuntu (64bit)```
    2. Select ```"Use existing hard disk"``` and if your newly cloned image doesn't appear in the drop-down list, click on the folder icon and find the image in your hard disk (the one you just cloned)
    3. Finish and test the image by starting the VM. You should get a prompt
    4. The credentials are ```user```/```superduper```
5. Now stop the machine and detach the hard drive from it (in settings)
6. Make the disk image immutable

    ``` 
    $ VBoxManage modifyhd ~/vmfest/models/ubuntu-10-10-64bit-server.vdi --type immutable
    ```

6. Get the name of your bridged network interface by running: 

    ```
    $ VBoxManage list bridgedifs | grep ^Name 
    ```
    e.g.```"Name: en1: AirPort 2"```  --> the interface name is ```"en1: Airport 2"```
    
7. Start VBbox Web Services: 

     ```
    $ vboxwebsrv -t0
    ```
    
8. In ```~/.pallet/config.clj``` add the following new provider:

    ``` clojure
    :virtualbox 
     {:provider "virtualbox"
      :images
       {:ubuntu-10-10-64bit
        {:description "Ubuntu 10.10 (64bit)"
         :uuid "4f072132-7fbc-431b-b5bf-4aa6a807398a" ;; the uuid if your cloned image
         :os-type-id "Ubuntu_64"
         :os-version "10.10"
         :username "user"
         :password "superduper"
         :os-family :ubuntu
         :os-64-bit true
         :no-sudo false
         :sudo-password "superduper"}}
     :model-path "~/vmfest/models"
     :node-path "~/vmfest/nodes"
     :url "http://localhost:18083"
     :identity ""
     :credential ""
     :environment
      {:algorithms
       {:lift-fn pallet.core/parallel-lift
        :vmfest {:create-nodes-fn pallet.compute.vmfest/parallel-create-nodes}
        :converge-fn pallet.core/parallel-adjust-node-counts}}
       :image
        {:bridged-network "Airport 2" ;; The name of the network interface to use for bridging 
       }}
    ```

9. Add the following dependency to your (lein/cake) project  ```[vmfest "0.2.2"]``` (under ```:dev-dependencies```)
10. Test pallet/vmfest. Download the dependencies and fire up the REPL. Then enter the following

    ``` clojure
    (use 'pallet.core)
    (use 'pallet.compute)
    (use 'pallet.crate.automated-admin-user)
    (def service (compute-service-from-config-file :virtualbox))
    (defnode test-node {:os-family :ubuntu :os-64-bit true} :bootstrap automated-admin-user)
    (converge {test-node 1} :compute service)
       ;; Observe in the VirtualBox GUI that a new VM named test-node-0 has been created and started
       ;; Wait for the REPL to return and get the ip
    ```
    
    * from a terminal, ssh into your newly created machine
    * pat yourself in the back
    * now kill your machine
    
        ``` clojure
        (converge {test-node 0} :compute service)
        ```
        
    * test the limits of your computer 
    
        ``` clojure
        (converge {test-node 10} :compute service) 
        ```
        
    * ... enjoy the music your computer fan makes!


## License
