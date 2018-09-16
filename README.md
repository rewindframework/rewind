[![Build Status](https://travis-ci.org/rewindframework/rewind.svg?branch=master)](https://travis-ci.org/rewindframework/rewind)

# Rewind

> When replaying your tests isn't enough.

Rewind framework was created to aide end-to-end testing especially when your tests needs to handle the coordination of multiple resources.
It isn't a real testing framework.
Instead, it's a set of clear defined concept where each concept's implementation can use whatever technology it wants.
It uses whatever testing framework you are most comfortable with.
Obviously, there are a set of technologies that work better together, and are a natural choice.

## Development

### Where are those concepts? I only see an opinionated implementation.
You are right.
As much as I would love to dedicate my time at developing this framework further, it is been improved on a as needed basis.
The concept are clear, they just need to be correctly layed out in an unopinionated way.
The framework has already been proven internally at an previous employment.
The implementation used Salt, Py.Test and Gradle as the major technologies.
For my current need, those technologies, with the exception of Gradle, are not the right fit, so the reimplementation is been done with a different sets of technologies.

## Concepts

> WARNING: The following is simply a brain dump, these will be refined later as time permit.

End-to-end testing tries to be as accurate as possible in term of environment setup.
If you will have latency on your network, then you should be able to control that latency during the tests.
If you interact with multiple machines with different operating system, then you should be able to provision those machines during the tests.
If you expect certain software to be installed and event some specific patches installed, the you should be able to ensure those are properly setup on your machines during the tests.
If you need a specific hardware for your test, then you should have access to it during the tests.

Basically, the resources needs to be made available to your tests.
Over the following sections, we will go over the various concepts Rewind as a framework use to be successful at allowing end-to-end testing.
This repository offer a reference implementation for some of the concepts and tries to standardize a language agnostic API so you can plug your own technologies inside.

### Coordinator

As the name imply the coordinator is in charge of coordinating the test execution.
Conceptually, it's where the testing framework will be executed.
Coordinator can be the local machine, it can be a static machine (or docker container), or it can be dynamic, provisioned as needed (unbounded or bounded to a resource pool).
Each type of coordinator have it's own reason for existing.
For example, your test may need Windows specific tools to complete and is then bounded to a Windows machine.
Although this could be worked around by requesting a Windows resource to execute those specific tool.
In general, the coordinator can be seen as a free space for the test code to mess around without affecting the a precious CI machine like installing services, starting services, etc.
Once the test is completed, the coordinator can be tear down and reprovisioned for the next test.

The coordinator can also be a machine running inside the testing environment which have access to a different virtual network and another set of resources.

The coordinator type are:
 - Isolated: alone on fresh machine
 - Shared: can be on another machine with other test running
 - None: don't really care

### Test Queue

The test queue's purpose is to receive test request and prepare the coordinator for execution.
If the coordinator is the local machine, then there's no need to go through the test queue.

### Testing Framework

Rewind doesn't provide any testing framework. In fact, it gives the flexibility to the user to use whatever testing framework it wants to use.
Off course some glue code may need to be provided to allow Rewind to correctly start the test package and return the test result back to the user.
This allows the user to be completely proefficient in what every testing framework it is used to.

### Automation Process

To allow everything to execute smoothly, the automation process is a key concept.
It's purpose is to administer everything from building the test code to packaging it for Rewind consumption to configuring the Rewind test run.
It's not small work, but tools, such as Gradle, can fill those big shoes.
Alternatively, you can use a series of scripts, and you can even throw some Perl scripts with XMLs in there.

### Rewind Package

For Rewind to consume and work flawlessly, it's package need to be standardized.
It's generally recommended to package all the resources you can inside a Rewind package.
The reason been, the package becomes self-sufficient and can stand the test of time, meaning the test can be reexecuted at a later time with the same result.
Some resources are a bit crazy to package inside such as a VM image or similar, however other service offers ways to deal with those kind of resource so you can simply have a reference to the resource instead.
In fact, lots of the infrastructure code for Rewind is recommended to be included inside the Rewind package for the simple reason that you can iterate faster on improving the bootstrap code or similar plugin to Rewind by sharing the infrastructure and between everyone instead of upgrading the service all the time and having to deal with versioning the Rewind infra.
For the miss behaving Rewind plugins, we can roll a system wide blacklist to prevent test from using corrupting or deadlock prone code.
However, the Rewind service offered as base implementation should be able to recover from those miss behavior in some way.

### Bootstrapping

The bootstrapping code serve as a first configuration steps. It's ran before and also after the test suit and is generally used to setup the coordinator with basic configuration... Althought thinking about it more, it should really just be installed by a kind of service from within the test as needed.

Let's think about this more.

Mostly, it setup the testing framework, starts it, then pull the result back.

### Resource Manager

This service is crucial in the management of the Rewind framework. It's whole purpose is to provide resource to a test.

 - Test request the resource it needs
 - The manager ensure the resource are allocated to only a single test (or suite)
 - The test heartbeat to the manager to keep his resource alive
 - The manager reclaim the resources if the test doesn't hearbeat enough
 - The test release resources once done
 - The manager block the test until the resources are available (all of them)
 - Each resource type can be handled by different service
 - The manager simply manage them by locking and unlocking them at any given moment
 - Each resource have a set of tags that resource request can match to
 - It is possible to match multiple resource or match a more complex resource for a less complex job. For example, you can request a Windows 8.1 x64 machine with patch X, Y, and Z installed or you can just ask for a Windows 8.1 machine not caring about the architecture or the patch or etc. These tags are really determined by the user of the system. Rewind make no assumption in to what they are.
