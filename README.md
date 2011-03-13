1. Install VirtualBox on your machine
2. Disable login credential: 
        $ VBoxManage setproperty websrvauthlibrary null
3. Download and uncompress the following image https://s3.amazonaws.com/vmfest-images/ubuntu-10-10-64bit-server.vdi.gz
4. Clone the image to the directory where you want it to be permanently stored:
        $ VBoxManage clonehd /path/to/downloaded/ubuntu-10-10-64bit-server.vdi /path/to/permanent/location/ubuntu...-server.vdi
    * This should produce a uuid for your new image. Keep it around
5. Start VirtualBox (the GUI) and:
    1. Create an new image Linux - Ubuntu (64bit)
    2. Select "Use existing hard disk" and if your newly cloned image doesn't appear in the drop-down list, click on the folder icon and find the image in your hard disk (the one you just cloned)
    3. Finish and test the image by starting the VM. You should get a prompt
    4. The credentials are user/superduper
5. Now stop the machine and detach the hard drive from it (in settings)
6. Make the disk image immutable
        $ VBoxManage modifyhd /path/to/permanent/location/ubuntu-10-10-64bit-server.vdi --type immutable
6. Get the name of your bridged network interface by running: 
        $ VBoxManage list bridgedifs | grep ^Name 
    e.g. "Name: en1: AirPort 2"  --> the interface name is "en1: Airport 2"
7. Start VBbox Web Services: 
        $ vboxwebsrv -t0
8. In ~/.pallet/config.clj add the following new provider:
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
         :model-path "/Users/tbatchlelli/.vmfest/models" ;; this should point to your home
         :node-path "/Users/tbatchelli/.vmfest/nodes" ;; this should point to your home
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

9. Add the following dependency to your project's [vmfest "0.2.2"] (under dev-dependencies)
10. Test pallet/vmfest. Download the dependencies (lein/cake/maven, whatever you use) and fire up the REPL
        (use 'pallet.core)
        (use 'pallet.compute)
        (use 'pallet.crate.automated-admin-user)
        (def service (compute-service-from-config-file :virtualbox))
        (defnode test-node {:os-family :ubuntu :os-64-bit true} :bootstrap automated-amdin-user)
        (converge {test-node 1} :compute service)
           ;; Observe in the VirtualBox GUI that a new VM named test-node-0 has been created and started
           ;; Wait for the REPL to return and get the ip
    * from a terminal, ssh into your newly created machine
    * pat yourself in the back
    * kill your machine
            (converge {test-node 0} :compute service)
    * test the limits of your computer 
            (converge {test-node 10} :compute service) 
    * ... enjoy the music your computer fan makes!


## License




