VMFest is a [PalletOps][palletops] project to turn [VirtualBox][vbox]
into a light-weight cloud provider. This is very useful for when
developing cloud automation. VirtualBox's Virtual Machines (VMs) boot
very quickly (seconds), so why not take advantage of it?

VMFest takes the form of a library, and you can use it as a toolkit to
create your own virtualization environments.

This project is work in progress, so feedback and suggestions are
welcome. Please use the
[issues](http://github.com/tbatchelli/vmfest/issues) for this purpose.

[vbox]: http://www.virtualbox.org 
[palletops]: http://palletops.com

# Notice

This is the latest stable release of the 0.2 version. New development
will be exclusively done in the 0.3.x code, which is now in the
[develop](vmfest/tree/develop) branch.  

0.3.0 will be the first release to not require the use of the web
services interface to VirtualBox, allowing the use of XPCOM too. If
your VirtualBox host is local to vmfest, using the XPCOM interface is
more convenient as it doesn't require starting up and configuring the
VirtualBox server. Give it a try!

# Release Notes

Can be found [here](vmfest/blob/support/v0.2.8/relnotes.md).

# Usage

## Install Virtualbox 4.2.x

Download and install the latest [VirtualBox 4.2][vbox]. Notice that
VMFest It won't work with any VirtualBox version version other than
4.2.

Start the VirtualBox server (`vboxwebsrv`) by issuing the following on the shell:

```bash
$ vboxwebsrv -t0
```

Finally, disable login authorization in virtualbox server: 

``` 
$ VBoxManage setproperty websrvauthlibrary null
```

[vbox]: https://www.virtualbox.org/wiki/Downloads

## Setup VMFest in your project

The following instructions are for setting up [Leiningen][lein]
project, just because pretty much everyone using Clojure uses `lein`,
but sticking vmfest in your classpath will suffice.

[lein]: https://github.com/technomancy/leiningen


Add the following dependencies to your ```project.clj```:

```clojure
   [vmfest "0.2.8"]
```

NOTE: add more detailed instructions for non-clojurians


# Basic Features

## Create VMs programmatically

VMFest has a data oriented API to create VMs. There are two data
structures needed to create a VM: one describing the hardware 
and the other one describing the image that the VM will boot off.

### Hardware specs

You can define every hardware aspect of a VM as a hash map. Consider
this map: 

```clojure
   {:memory-size 512
    :cpu-count 1
    :network [{:attachment-type :host-only
               :host-only-interface "vboxnet0"}
              {:attachment-type :nat}]
    :storage [{:name "IDE Controller"
               :bus :ide
               :devices [nil nil {:device-type :dvd} nil]}]
    :boot-mount-point ["IDE Controller" 0]}}
```

This map describes a VM with 1 CPU, 512MB RAM, two network interfaces
plugged in: one attached to VirtualBox's host-only network named
"vboxnet0", and another one attached to NAT. For storage it has an IDE
controller in the first slot. The first channel in this IDE controller
and the second channel has a DVD attached to the master. Finally, a
the booting image (will cover this later) will be attached in the
master slot master in the channel 1 of the IDE.


### Image specs

You can use any VirtualBox image with VMFest, but we encourage using
immutable ones, a must if you want to use VMFest as a cloud provider.

VMFest lets you provide image medatata that will be passed on to the
libraries using VMFest. This image metadata can contain information
like the user/password for the image, the OS family, 32 or 64 bits,
etc., but for now we only care about one piece of data: the image
location in the file system --which for historical reasons it is
called uuid:

```clojure
{:uuid
"/Users/tbatchelli/images/vmfest-Debian-6.0.2.1-64bit-v0.3.vdi"}
```

### Create VMs from specs

A new VM is created from a hardware spec and an image spec. The
following will create a VM on the Virtualbox host defined by
`my-server`.  

```clojure
(def my-machine 
     (instance my-server "my-vmfest-vm" 
        {:uuid "/Users/tbatchelli/imgs/vmfest-Debian-6.0.2.1-64bit-v0.3.vdi"}  
        {:memory-size 512
         :cpu-count 1
         :network [{:attachment-type :host-only
                    :host-only-interface "vboxnet0"}
                   {:attachment-type :nat}]
         :storage [{:name "IDE Controller"
                    :bus :ide
                    :devices [nil nil {:device-type :dvd} nil]}]
         :boot-mount-point ["IDE Controller" 0]}))
```

## Manage VMs

You can `start`, `pause`, `resume`, `stop`, `power-down` and `destroy` (careful
with `destroy` if you are not using immutable images!). It shouldn't
be too hard to figure out what they do. The only operation that needs
some explanation is `destroy`, as it will remove the VM and __also the
attached image__ if it is not immutable.

Each of these functions take a machine as argument, e.g.:

```clojure
(start my-machine)
(pause my-machine)
(resume my-machine)
(stop my-machine)
(destroy my-machine)
```

Operating on existing machines is also possible:

```clojure
(def my-machine (find-machine my-server "a-machine"))
(start my-machine)
```

Finally, you can get the IP address of your VMs, provided that the
images the VMs are running have VirtualBox Guest Additions installed:

```clojure
(get-ip my-machine)
```

# Cloudy features

## Models

The main use case that we had when we built VMFest was to use
VirtualBox like a lightweight cloud provider.

Most clloud providers will tipically provide a set of images and a set
hardware profiles, and when you want to create a new instance (VM) you
select a profile for each, e.g., ubuntu 10.04 + Large Machine.

To emulate this convention, vmfest has these two concepts: _Image
Models_ and _Hardware Models_. These are lists of named specs, both
for image and hardware. These specs are built with the clojure maps
described above, and you can add your own too.

When creating a new VM, you can reference these specs by key instead
of passing the specs. For example, in my laptop, I can instantiate the
exact same VM as above with: 

```clojure
(instance my-server "my-vmfest-vm" :debian-6.0.2.1-64bit-v0.3 :micro)
```

### Setting up Image Models

We have created a few images to be used with VMFest. Although VMFest
can operate on any image, we want to build high quality images that
every one can confidently use. We also provide a way to setup those
images on your local VMFest setup.

```clojure
(setup-model "https://s3.amazonaws.com/vmfest-images/debian-6.0.2.1-64bit-v0.3.vdi.gz" my-server)
```

This will download and install this image as immutable. From there on
you can start using it as :debian-6.0.2.1-64bit-v0.3.

## Fast VM Instantiation

VirtualBox provides a way to run many VMs out of the same disk image.
This is done using immutable disk images. When a image is set as
immutable, every time you attach new VM to this disk, VirtualBox
creates a `differencing disk`. This differencing disk contains the
disk sectors from the original disk that have changed, or the
difference. Each VM attached to an immutable image can diverge
independently once it is booted.

The benefit of using immutable images this way is that in order to
start two or more VMs out of the same exact disk image, you don't have
to create one copy the original disk image for each VM. This results in big
savings in terms of time and most importantly, space.

When you use VMFest then, you can create as many VMs as you please and
attach them to the same image. This is usually a very fast operation,
typically sub-second. Here is one example of how to do this:

```clojure
;; create some names for the VMs
(def names ["slave-1" "slave-2" ... "slave-N" "master"])

;; instantiate the VMs
(def machines 
     (map (fn [name] 
              (instance my-server % :debian-6.0.2.1-64bit-v0.3 :micro))
          names))
          
;; start all the VMs
(map start machines)
```

# Low Level API 

There is a low-level API to program VirtualBox from Cloure. This API
eliminates some of the complexity of connecting to VirtualBox, but
we'll leave this explanation for another day. Just know that it's
there, and you can look at the `manager.clj` sources and the tests to
see what this API is about.

# Tutorial

We've created a [playground project][playground] for you to test
VMFest. You can find the tutorial [here][tutorial].

[playground]: https://github.com/pallet/vmfest-playground
[tutorial]: https://github.com/pallet/vmfest-playground/blob/master/src/play.clj

```clojure
(use 'vmfest.manager)
(use '[vmfest.virtualbox.image :only [setup-model]])

;; First we need to define a connection to our VirtualBox host
;; service.
(def my-server (server "http://localhost:18083"))

;; We need an image model to play with. This will set up a fairly up-to-date
;; Debian image.
(setup-model "https://s3.amazonaws.com/vmfest-images/debian-6.0.2.1-64bit-v0.3.vdi.gz" my-server)
;; {:image-file "/var/folders...}

;; let's check that the image model has been installed
(models)
;; (:debian-6.0.2.1-64bit-v0.3) <-- you should see this

;; Time do create a VM instance. We'll call it my-vmfest-vm. This is
;; the name that will appear in VirtualBox's GUI.
(def my-machine (instance my-server "my-vmfest-vm" :debian-6.0.2.1-64bit-v0.3 :micro))

;; Notice that once we have created a VM we don't need to reference
;; the server anymore
(start my-machine)

;; Get the IP address of the machine. At this point, you can SSH into
;; this machine with user/password: vmfest/vmfest
(get-ip my-machine)

;; You can pause and resume the VM.
(pause my-machine)
(resume my-machine)

;; Stopping the VM will send a signal to the OS to shutdown. This will
;; not the VM itself, just the OS run by the VM
(stop my-machine)

;; This will turn off the VM completely and immediately.
(power-down my-machine)

;; Once we are done with this VM, we can destroy it, which will remove
;; any trace of it's existence. Your data will be lost, but not the
;; original image this VM was booted off.
(destroy my-machine)


;;; MULTIPLE INSTANCES

;; Now we are going to create multiple instances of the same image.
;; First we need some names for each instance. names will do just
;; that, e.g.: (names 3) -> ("vmfest-0" "vmfest-1" "vmfest-2").
(defn
  names [n]
  (map #(format "vmfest-%s" %) (range n)))

;; This function will create a debian instance based on the image
;; downloaded above
(defn deb-instance [server name]
  (instance server name :debian-6.0.2.1-64bit-v0.3 :micro))

;; Let's create a few images. Notice that in this case we're creating
;; 5. Each machine takes roughly 0.5GB of RAM, so change the number to
;; match your available RAM.
(def my-machines (pmap #(deb-instance my-server %)
                       (names 5)))

;; From here we can start, power-down, and destroy all the VMs in parallel.
(pmap start my-machines)
(pmap power-down my-machines)
(pmap destroy my-machines)
```
# Contact

If you need help setting up or programming VMFest, or have
suggestions, or just want to chat, here are your options:

 - Join the [Pallet mailing list][pallet-ml] and send a message
 - Join the #pallet channel on [FreeNode's IRC][freenode]
 - Create a new issue (feat request, bug, etc.) on
   [vmfest's github repo][vmfest-issues]
 - Tweet [@palletops][palletops-tweet]
 
# License

Copyright © 2012 Antoni Batchelli

Distributed under the Eclipse Public License, the same as Clojure.
 
[pallet-ml]: https://groups.google.com/forum/?fromgroups#!forum/pallet-clj

[freenode]: http://freenode.net/irc_servers.shtml

[vmfest-issues]: https://github.com/tbatchelli/vmfest/issues

[palletops-tweet]: https://twitter.com/palletops
