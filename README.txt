JConsole Swing Component
========================

Dependencies: -

Description: A Swing component to emulate a terminal console in a Swing application.
             It provides input, output and error streams which can be provided to other parts of the application
             which need console access. Published under MIT License.

Characteristics
- After constructing the JConsole, 3 streams are available to the user: input, output and error.
- The user can issue commands from outside the component by calling the 'setCommand()' method.

Build instructions:
 * It is a Maven project, there are no dependencies.
 * There is a small test which installs the component in a JFrame, it shows how to use the component.

References: -