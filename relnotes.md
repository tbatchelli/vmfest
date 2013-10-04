# Release Notes

## 0.3.0-beta.3

- Fix issue images would not install on a non-root filesystem.

## 0.3.0-beta.2

- Fix regression: ~/.vmfest/models not created on first image install.

## 0.3.0-beta.1

- add clojure 1.5.1 profile

- Fix documentation re: setup of Vagrant boxes.

- Allow hardware config overrides in .meta files
  Add hardware DSL map under :hardware in .meta file, e.g:

   ```clojure
   {:os-family ... :hardware {:io-apic-enabled? true ...}}
   ``` 

- Skip loading models when image file is not present.

- Speed up image installation.
  We do not need to clone .dvi images anymore to register them, as they are
  automatically registered before each use to workaround VBox forgetting
  their mutability status. This saves space and time.

  We are still cloning when installing vagrant boxes.

- Merge pull request #65 from sumbach/patch-3
  Consistently capitalize VirtualBox in README
- Merge pull request #63 from sumbach/patch-1
  Fix minor typos in README
- Consistently capitalize VirtualBox

- Fix minor typos

- Fix typo in manager/model-info. Fixes #62

## 0.3.0-alpha.5

-  Fix issue with installing 32bit Vagrant images.

## 0.3.0-alpha.4

- Allow late addition of vbox library in the classpath.

- Add diagnostics function in vmfest/manager.
  `(manager/diagnostics my-server)` will return a map with relevant
  information about the state and configuration of the host and vmfest
  models and machines.

- Prevent VMs from starting if host doesn't support guest OS.
  If the Guest is a 64bit os but the host's CPU doesn't provide the
  hardware virtualization extensions necessary for running virtualized
  64bit OS, prevent the VM from starting. Fixes #60.

- Fix host-only interface automatic creation. Fixes #58.

## 0.3.0-alpha.3

- Make manager/power-down more stable.
  Before, power-down would return even when the VM was not in a lockable 
  state. Now it polls for the state until it is lockable. This was causing
  calls to destroy after a power down to fail in some instances because of
  this race condition. Might be a bug in VBox.

- Add support for VMs mounting shared host directories. Closes #57
  When a local "folder" is shared, for example "/tmp" shared as
  "my-tmp", then the shared folder can be mounted by the VM's OS by issuing
  the following at the command line:

  ```shell
  $ mount -t vboxfs my-tmp /mnt/tmp
  ```

  This is done at the Hardware DSL level, by adding a new top level entry
  :shared-folders that contains a vector of shares. Each share is a vector
  of share name, host path, auto-mount?, writable?, the last two being
  optional and defaulting to false and true (no auto-mount, writable), e.g.

  ```clojure
  {...
  :shared-folders [["my-tmp" "/tmp" nil true] ["my-var" "/var"]]
  ...}
  ```

- Fix issue with destroy after power-down failing sometimes Fixes #56.
  destroy will now wait for the VM to be in a lockable state before 
  attempting to lock. Apparently power-down returns leaving a temporary 
  lock on the VM, and I can't explain why atm.

- Refine support for setting network adapter type (chipset).

- Add disk image creation for Hardware DSL.
  With this change, when specifying the storage device to attach to a 
  storage controller, you can define an image to be created.

  e.g.  {:device-type :hard-disk
        :location disk-location
        :create? true
        :size disk-in-mb
        :attachment-type :normal}

  The presence of :created? (set to true), :location and :size will prompt 
  the creation of the disk image. Other optional keys are :variants and
  :format (see image/create-medium).

- Add 'nuke' to manager to unconditionally destroy a VM.

- Fix IO-APIC for when # CPU > 1. fixes #55
  When the HW DSL specifies more than one CPU, the system will 
  automatically enable :io-apic-enabled?

- Add v.v.v/vbox-info to get a map with vbox host's info
  This includes virtualbox info (e.g. api version, ws/xpcom, etc.), and 
  also host info (cpus, memory, O, host network interfaces and their DHCP
  config, etc)

## 0.3.0-alpha.2

- Add Vagrant support info on README.

- Enforce psssing of os-family, etc, for .box images
  The information is unavailable in the .box file, so has to be supplied
  manually.

- Add usage of public vagrant key for .box images

- Fix the virtualbox metadata
  When options are supplied, merge them rather than rely only on the
  options passed.

- Remove clj-http dependency

- Add get-network-adapters and get-network-interfaces
  Add functions in manager and machine to get network interface and
  adapters as maps.

- Add import of vagrant .box urls to setup-model

- Merge pull request #47 from briprowe/develop
  Add support for NAT port-forwarding rules in network adapters.

- configure-network can now add port-forwarding rules to adapters.
  Example: to configure an adapter that port forwards the host's port 8008
  to the guest's port 80:

  (configure-network machine {:attachment-type :nat
                             :nat-rules [{:name "http", :protocol :tcp,
                                          :host-ip "", :host-port 8080,
                                          :guest-ip "", :guest-port 80}]})


- Ensure model images are immutable before use (Fixes #46)

- Remove reflection warnings

- Factor out lock-machine and unlock-machine

- Update session cleanup and disconnect

## 0.3.0-alpha.1

- Update README with the new features in 0.3.0.

- Provide support for the XPCOM bridge along with WS.
  - Code now supports XPCOM or WS depending of what vbox jar is in the
   classpath.
  - Requires lein 2.0 now, and running tests requires combining
   profiles, e.g. "dev,xpcom,1.3" to test the xpcom bridge using
   clojure 1.3

- Make vbox download link in readme to be version agnostic.

- Bark when running an unsupported version of VirtualBox

- List machines by group(s). Add function to list vmfest-managed machines.

- Make "http://localhost:18083" the default server URL when none is passed.

## 0.2.6-beta.1

- Update README to version 0.2.6-beta.1 + VBox 4.2

- Adapt vmfest to VirtualBox 4.2
    - Removed `find-medium` after the removal from the API. Use
     `open-medium` instead
   - VMfest now creates all nodes in the "vmfest" group (new feature of
     4.2)

- Incorporate vbox's API function/attribute name changes for 4.2.x

## 0.2.5

- Add support for get-extra-data-keys
  This allows the extra data to be enumerated.

## 0.2.4

Same as 0.2.4-beta.5

## 0.2.4-beta.5

- Attempt to create the nodes directory if it doesn't exist.

- Fix throw+ when node-path doesn't exist
  This was missed in the conversion from contrib.condition. Fixes #19.

## 0.2.4-beta.4

- Fix issue in add-one-host-only-interface
  In clojure 1.3.0, the -1 argument was being wrapped as a Long, and
  reflection was failing.

- Automatically create host-only interfaces with DHCP servers when they are
  referenced and don't exist.
   - Only interfaces named vboxnetN are attempted to be created, others
    will produce error.
  - The default DHCP configuration puts the DHCP server in x.x.x.100
    controlling addresses from x.x.x.101 to x.x.x.254
  - Added a new v.v.host namespace.

  NOTE: DHCP servers are named "HostInterfaceNetworking-vboxnetN", so the
  functions in virtualbox.clj dealing with finding and creating DHCP server
  will take vboxnetN and internally convert to the name above. This might
  change in the future.

- Cleanup debug prints in machine-config

- Remove the need to call manager/update-models.

- Restore functionality: vbox/find-medium to return nil when none is found.

- Add mising paraen in image.clj

- Clean up log calls in image.clj.

- Ensure that the model is registered before creating a machine.

## 0.2.4-beta.3

- Allow getting the IP address of different network card slots. Closes #17.

- Rename variable 'key' to something less confusing

- fix a typo

- Fix documentation to add a call to (update-models) at the beginning of a
  session.

- Fix typo in Leiningen link.

- README.md: added more instruction for the example

- Fixed typo

- Fixed typo in instructions for v0.2.3

# 0.2.4-beta.2

- Fixed the comments in the last commit on this file, as the strings were not
  scaped

- Fix codumentation typo.

- Document conditions.

- Add the possibility of passing image model and hardware config maps
  directly to 'instance'

# 0.2.4-beta.1

- Fix the multi-dependencies set-up to provide a default dependency list

- Add support for testing with multiple clojure versions via lein multi

- Update .gitignore

- Fix spelling of machine :accessible? attribute

- Add missing import

- Fix spelling of error message

- Change setup-model back to return the job
  When using setup-model, it is useful to have access to the metadata of
  the new image, so an external list of images may be maintained (used by
  pallet-vmfest)

- Add missing parameter for 4.1 openMedium calls

- Remove recur across try error

- Fix issue with manager.clj not fully ported to 1.3 (need tests too)
  - :micro instances are now non-bridged by default. Makes life easier for 
  people testing vmfest for the first time.
  - fixed :micro hardware model to correctly set the host-only interface

- Fix harcoded reference to vbox 4.0

- Set default clojure dependency to 1.2.1

- Added missing dot that prevented `(use 'vmfest.manager)` from working.

- Update to Clojure 1.3.0 and VirtualBox 4.1.x
  Remove all references to clojure.contrib Adapt network configuration to
  the new API in vbox 4.1 Enforce Integer type when calling Java methods
  that expect Integers.

- Make waiting for a lockable state more stable

- Allow creating instances without needing to use the *images* and
  *machine-models* lists

- Add infrastructure needed to support multiple versions of VirtualBox.

- Update docs to reflect that this still doesn't work for VBox 4.1.x

- Allow for host-only networking (i.e. no need of bridging)
  Enable NAT and host-only attachments. Add micro-internal hardware profile
  in manager to define VMs that use host-only networking (they have one
  host-only interface and one NAT interface)

- Add the functions models, model-info and check-model to manager.
  - models lists the keys of the models loaded by the system
  - model-info will provide the metadata of the model corresponding to the
  supplied key
  - check-model will check if a model corresponding to the key is valid
  (registered and immutable)

  Also, setup-model now returns the key of the installed model.

- Make newly installed models match the original name in the model map,
  without "vmfest-" in front of it. The files will remain with "vmfest-" in
  front of them so they can be identified.

- Rename key :models-dir in image setup job to :model-path to be consistent
  with the rest of the code.

- Add functions to manager to determine which host network interface to use
  for bridging.
  Add comments to functions.

- Add DSL to configure bridged network interfaces.

- Add storage-building DSL to allow the creation of the storage part of a VM
  from a data structure.
  See machine-builder-tests for more information on how the data structure
  looks like. Needs more thorough testing of the configuration maps passed
  as parameters.

- Update manager.clj to use machine-config for machine configuration.
  Manager now uses machine-config to instantiate machines.
  machine-config/configure-machine had to be split into configure-machine
  and configure-machine-storage since you can't edit the storage on a
  machine that hasn't been registered. Lame, I know! I might be wrong
  too... Add entry in machine config map to specify where the boot image is
  to be mounted. Change logging from c.c.logging to c.t.logging and updated
  log statements accordingly. Add a function in manager that mounts the
  image to the right bus/port according to :boot-mount-point in the machine
  config map.

- Add DSL to configure bridged network interfaces.

- Add storage-building DSL to allow the creation of the storage part of a VM
  from a data structure.
  See machine-builder-tests for more information on how the data structure
  looks like. Needs more thorough testing of the configuration maps passed
  as parameters.

- Rename key :models-dir in image setup job to :model-path to be consistent
  with the rest of the code.

