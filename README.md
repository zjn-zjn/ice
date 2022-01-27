<img width="128" alt="5" src="https://user-images.githubusercontent.com/33447125/151098049-72aaf8d1-b759-4d84-bf6b-1a2260033582.png">

[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)
[![Badge](https://img.shields.io/badge/link-ice--docs-green)](http://waitmoon.com/docs)

> http://waitmoon.com/docs
- en [English](README.md)
- cn [简体中文](README.cn.md)

# Ice introduction

> Committed to solving flexible and complex hard-coded problems

## Background

We are not unfamiliar with rule/process engines. Drools, Esper, Activiti, Flowable, etc. are familiar to everyone. Many big manufacturers are also keen to study their own rule engines, which are used to solve complex rules and process problems in flexible scenarios. To change the configuration, you can generate/validate new rules, and get rid of the hard-coded bitter sea. After all, changing the configuration and arranging rules/processes on an existing basis is much cheaper than hard coding, but using the existing rule engine on the market to arrange, firstly, the access cost and learning cost are not low, and secondly With the passage of time, the rules have become more and more complex and some scenarios are not applicable, which makes people complain even more.

## Design ideas

In order to facilitate understanding, the design idea will be accompanied by a simple recharge example.

### Example

Company X will carry out a seven-day recharge activity. The contents of the activity are as follows:

**Activity time:**(10.1-10.7)

**Activities:**

Recharge 100 dollar, get 5 dollar balance(10.1-10.7)

Recharge 50 dollar, get 10 points(10.5-10.7)

**Event Notes:** No superimposed gift (recharge 100 dollar can only get 5 dollar balance, will not be superimposed to give 10 points)

Simply dismantling, to complete this activity, we need to develop the following modules:

<img width="719" alt="2" src="https://user-images.githubusercontent.com/33447125/148500633-654597e0-ed9c-4e0c-9060-7b5751ed72db.png">

As shown in the figure, when the user recharges successfully, a parameter package Pack (like Activiti/Drools Fact) corresponding to the recharge scenario will be generated. The package will contain the recharge user's uid, recharge amount cost, recharge time requestTime and other information. We can get the value in the package through the defined key (similar to map.get(key)).

There is nothing wrong with how the module is designed. The key point is how to arrange the following to achieve freedom of configuration. Next, through the existing nodes above, we will explain the advantages and disadvantages of different rule engines in the core arrangement, and compare how ice does it.

### Flowchart implementation

Like Activiti, Flowable implementation:

<img width="779" alt="3" src="https://user-images.githubusercontent.com/33447125/148500648-6964373f-f810-4406-9b70-580f8725513f.png">

Flowchart implementation should be the most common arrangement method we think of~ It looks very concise and easy to understand. Through special design, such as removing some unnecessary lines, the UI can be made more concise. But because of the time attribute, time is actually a rule condition, and after adding it becomes:

<img width="1103" alt="4" src="https://user-images.githubusercontent.com/33447125/148500657-c08e014d-014d-4526-8612-3414c4d8ab87.png">

looks ok too

### Execution tree implementation

Like Drools implementation (When X Then Y):

<img width="1110" alt="5" src="https://user-images.githubusercontent.com/33447125/148500665-d59127a6-092b-4203-99de-01c4f807e84f.png">

This looks fine too, try it with the timeline:

<img width="1648" alt="6" src="https://user-images.githubusercontent.com/33447125/148500674-297dbe13-efaf-41f8-b362-2cbbdc5613cb.png">

It's still relatively concise, at least compared to the flowchart format, and I would be more willing to modify this.

### Change

The advantage of the above two solutions is that some scattered configurations can be well managed in combination with the business, and minor modifications to the configuration can be done at your fingertips, but in real business scenarios, you may still have to hammer you. With flexibility changes, everything is different.

#### Ideal

*It won't change, don't worry, that's it, go online*

#### Reality

①Change the recharge from 100 dollar to 80, change 10 points to 20 points, and change the time to 10.8 and end it (*smile*, after all, I spent so much time working on the rules engine, and it finally shows its value!)

②Users are not very motivated to participate, so let’s get rid of the non-overlay and send them all (*think about it a little*, it’s okay to spend a few brain cells to move around, it’s better than changing the code and then going online!)

③The balance of 5 dollar can't be given too much. Let's set up an inventory of 100. By the way, if the inventory is insufficient, you will still have to send 10 points to charge 100 dollar (*dead...* If you knew it earlier, it would be better to hard code)

The above changes do not seem unrealistic. After all, the real online changes are much more outrageous than this. The main disadvantage of the flow chart and execution tree implementations is that they can affect the whole body. It is easy to make mistakes if it is not considered in place, and this is just a simple example. The actual content of activities is much more complicated than this, and there are also many timelines. Considering this, plus the cost of using the learning framework, Often the gain outweighs the gain, and in the end it turns out that it's better to hardcode it.

what to do?

### How is ice made?

#### Introduce relation nodes

In order to control the flow of business, the relation node

**AND**

Among all child nodes, one returns false, and the node will also be false, all true is true, and the execution is terminated at the place where false is executed, similar to Java's &&

**ANY**

Among all child nodes, if one returns true, the node will also be true, all false are false, and the execution will be terminated when the execution reaches true, similar to Java's ||

**ALL**

All child nodes will be executed. If any one returns true, the node is also true. If there is no true, if a node is false, it will return false. If there is no true and no false, it will return none. All child nodes are terminated after execution.

**NONE**

All child nodes will execute, no matter what the child node returns, it will return none

**TRUE**

All child nodes will be executed, no matter what the child node returns, it will return true, and no child node will return true (other nodes without children return none)

#### Introduce leaf nodes

The leaf node is the real processing node

**Flow**

Some condition and rule nodes, such as ScoreFlow in the example

**Result**

Nodes with some result properties, such as AmountResult, PointResult in the example

**None**

Some actions that do not interfere with the process, such as assembly work, etc., such as TimeChangeNone will be introduced below

With the above nodes, how do we assemble it?

<img width="599" alt="7" src="https://user-images.githubusercontent.com/33447125/148500688-d67c76c1-add4-42b1-bd63-c9f99d3cfeab.png">

As shown in the figure, using the tree structure (the traditional tree is mirrored and rotated), the execution order is similar to in-order traversal. It is executed from the root, which is a relationship node, and the child nodes are executed from top to bottom. If the user's recharge amount is 70 Element, the execution process:

[ScoreFlow-100:false]→[AND:false]→[ScoreFlow-50:true]→[PointResult:true]→[AND:true]→[ANY:true]

At this time, it can be seen that the time that needs to be stripped out before can be integrated into each node, and the time configuration is returned to the node. If the execution time is not reached, such as the node that issued the points will take effect after 10.5 days, then before 10.5 , it can be understood that this node does not exist.

#### Changes and problem resolution

For ① directly modify the node configuration

For ②, you can directly change the ANY of the root node to ALL (the logic of superimposed and non-superimposed transmission is on this node, and the logic belonging to this node should be solved by this node)

For ③ due to insufficient inventory, it is equivalent to not issuing to the user, then AmountResult returns false, and the process will continue to be executed downwards without any changes.

One more thorny question, when the timeline is complex, what to do with test work and test concurrency?

An event that started in 10.1 must be developed and launched before 10.1. For example, how do I test an event that started in 10.1 in 9.15? In ice, it just needs to be modified slightly:

<img width="738" alt="8" src="https://user-images.githubusercontent.com/33447125/148500698-66bd662a-0f14-4cb8-9cb1-b2a0886fcba3.png">

As shown in the figure, a node TimeChangeNone (changing the requestTime in the package) is introduced, which is responsible for changing the time. The execution of the subsequent nodes depends on the time in the package. TimeChangeNone is similar to a plug-in for changing time. If the test is parallel, then You can add a time change plug-in to the business that each person is responsible for for multiple tests.

#### Characteristic

Why dismantle it like this? Why does this solve these changes and problems?

In fact, the tree structure is used for decoupling. When the flow chart and execution tree are implemented, when changing the logic, it is inevitable to look forward and backward, but ice does not need it. The business logic of ice is all on this node, and each node can represent a single Logic, for example, if I change from stacking to stacking, the logic is only limited to the logic of that ANY node. Just change it to the logic I want. As for the child nodes, don't pay special attention. It depends on the flow of packages, and the subsequent process executed by each node does not need to be specified by itself.

Because the execution process after executing it is no longer under its control, it can be reused:

<img width="1338" alt="9" src="https://user-images.githubusercontent.com/33447125/151101151-4f2ebc59-d588-4385-84ee-750416127e1a.png">

As shown in the figure, TimeChangeNone is used here for participating in the activity. If there is still an H5 page that needs to be presented, and different presentations are also related to time, what should I do? Just use the same instance in the render activity, change one, and the other will be updated, avoiding the problem of changing the time everywhere.

In the same way, if there is a problem on the line, such as the sendAmount interface hangs, because the error will not return false to continue execution, but provide optional strategies, such as the Pack and the node to which it is executed, and wait until the interface is repaired. , and then continue to throw it into ice and run it again (because the time of placing the order is the time when the problem occurs, there is no need to worry about the problem that the repair after the event ends will not take effect). Similarly, if it is a non-critical business such as the avatar service, it still hangs. I hope to run, but there is no avatar, so you can choose to skip the error and continue to execute. The rules for placing orders here are not described in detail. The same principle can also be used for mocks. Just add the data that needs to be mocked in the Pack, and you can run it.

#### Introduce the forward node

<img width="453" alt="10" src="https://user-images.githubusercontent.com/33447125/148500727-dc3a3bac-eec7-4287-8360-262e65c9874b.png">

In the above logic, we can see that some AND nodes are closely bound. In order to simplify the view and configuration, the concept of forward node is added. This node will be executed if and only when the execution result of the current node is not false. , the semantics are consistent with the two nodes connected by AND.
