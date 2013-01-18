# Release Notes

## 0.2.6

- Project and documentation updates for release

## 0.2.6-beta.1

- Support VirtualBox 4.2.x. 
      - Removed `find-medium` after the removal from the API. Use
        `open-medium` instead
      - VMfest now creates all nodes in the "vmfest" group (new
        feature of 4.2)
      - Incorporate vbox's API function/attribute name changes for
        4.2.x

- Drop support for VirtualBox 4.1.x or older


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

