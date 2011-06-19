/*
 * Library "lib-jconsole".
 * Copyright (c) 2011 Bruno Ranschaert, SDI-Consulting BVBA.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.sdicons.jconsole;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * A Swing component to emulate a terminal console in a Swing application.
   It provides input, output and error streams which can be provided to other parts of the application
   which need console access.
 */
public class JConsole
extends JScrollPane
{
    // The console streams managed by the component.
    private OutputStream fromConsoleStream;
    private InputStream in;
    private PrintStream out, err;
    private AttributeSet attrIn, attrOut, attrError;
    private PipePump outPump, errPump;

    // Character nr of the start of the current command within the text component.
    private int commandPos = 0;
    // Command history, accepted commands are added at the end of the list.
    private List<String> commandHistory = new ArrayList<String>();
    // The index runs from the last command to the first, so index = 0 points
    // the the current command in the list and index = size points to the first one.
    private int commandHistoryIndex = 0;
    // The current commandline is accumulated here.
    private String currentCommand;
    // The text component representing our console.
    private JTextPane text;

    // Context menu stuff.
    private JPopupMenu contextMenu;
    private final static String CMD_CUT = "Cut";
    private final static String CMD_COPY = "Copy";
    private final static String CMD_PASTE = "Paste";

    public JConsole()
    {
        super();

        text = new MyJTextPane();

        final Font lFont = new Font("Monospaced", Font.PLAIN, 12);
        text.setText("");
        text.setFont(lFont);
        text.setMargin(new Insets(5, 3, 5, 3));
        text.addKeyListener(new MyKeyListener());
        setViewportView(text);

        contextMenu = new JPopupMenu();
        final ActionListener lActionListener = new MyActionListener();
        contextMenu.add(new JMenuItem(CMD_CUT)).addActionListener(lActionListener);
        contextMenu.add(new JMenuItem(CMD_COPY)).addActionListener(lActionListener);
        contextMenu.add(new JMenuItem(CMD_PASTE)).addActionListener(lActionListener);
        text.addMouseListener(new MyMouseListener());

        MutableAttributeSet attr = new SimpleAttributeSet();
        StyleConstants.setForeground(attr, Color.BLACK);
        attrIn = attr;

        attr = new SimpleAttributeSet();
        StyleConstants.setForeground(attr, Color.BLUE);
        attrOut = attr;

        attr = new SimpleAttributeSet();
        StyleConstants.setForeground(attr, Color.RED);
        StyleConstants.setItalic(attr, true);
        StyleConstants.setBold(attr, true);
        attrError = attr;

        try
        {
            fromConsoleStream = new PipedOutputStream();
            in = new PipedInputStream((PipedOutputStream) fromConsoleStream);

            final PipedOutputStream lOutPipe = new PipedOutputStream();
            out = new PrintStream(lOutPipe);
            final InputStream lNormalToConsoleStream = new PipedInputStream(lOutPipe);

            final PipedOutputStream lErrPipe = new PipedOutputStream();
            err = new PrintStream(lErrPipe);
            final InputStream lErrorToConsoleStream = new PipedInputStream(lErrPipe);

            errPump = new PipePump(lErrorToConsoleStream, attrError);
            final Thread lErrThread = new Thread(errPump);
            lErrThread.setDaemon(true);
            lErrThread.setPriority(Thread.NORM_PRIORITY + 1);
            lErrThread.start();

            outPump = new PipePump(lNormalToConsoleStream, attrOut);
            final Thread lNormalThread = new Thread(outPump);
            lNormalThread.setDaemon(true);
            lNormalThread.setPriority(Thread.NORM_PRIORITY - 1);
            lNormalThread.start();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        requestFocus();
    }

    /**
     * Set the text attributes that will be used for the text the user enters.
     *
     * @param aAttribs Text attributes.
     */
    public void setInAttributes(AttributeSet aAttribs)
    {
        attrIn = aAttribs;
        setStyle(aAttribs, true);
    }

    /**
     * Set the attributes that will be used for the output stream to the console,
     * the normal output.
     *
     * @param aAttribs Text attributes.
     */
    public void setOutAttributes(AttributeSet aAttribs)
    {
        attrOut = aAttribs;
        outPump.setAttr(aAttribs);
    }

    /**
     * Set the attributes that will be used for the error stream to the console,
     * the error output.
     *
     * @param aAttribs Text attributes.
     */
    public void setErrAttributes(AttributeSet aAttribs)
    {
        attrError = aAttribs;
        errPump.setAttr(aAttribs);
    }

    /**
     * Get the input stream containing the text the user enters in the console.
     *
     * @return An input stream from which the user commands can be read.
     */
    public InputStream getInputStream()
    {
        return in;
    }

    /**
     * Get the input reader containing the text the user enters in the console.
     *
     * @return A text reader from which the user commands can be read.
     */
    public Reader getIn()
    {
        return new InputStreamReader(in);
    }

    /**
     * The output stream to the console.
     *
     * @return An output stream on which the application can write text to the console.
     */
    public PrintStream getOut()
    {
        return out;
    }

    /**
     * The error stream to the console.
     *
     * @return An output stream on which the application can write text to the console.
     */
    public PrintStream getErr()
    {
        return err;
    }

    // Focus handling.
    public void requestFocus()
    {
        super.requestFocus();
        text.requestFocus();
    }

    // Remember the start of the command line.
    private void initCommandPos()
    {
        commandPos = textLength();
    }

    // Append text to the end of the text already present in the
    // text component.
    private void appendConsoleText(String aContent)
    {
        final int lTxtLen = textLength();
        text.select(lTxtLen, lTxtLen);
        text.replaceSelection(aContent);
    }

    // Replace part of the text in the text component.
    private String replaceConsoleText(Object aContent, int aFrom, int aTo)
    {
        final String aContentRepr = aContent.toString();
        text.select(aFrom, aTo);
        text.replaceSelection(aContentRepr);
        return aContentRepr;
    }

    // Move the editing caret to the end of the text.
    private void moveCaret()
    {
        if (text.getCaretPosition() < commandPos)
        {
            text.setCaretPosition(textLength());
        }
        text.repaint();
    }

    // If the user presses newline in the console, the current line is ready for consumption by the
    // rest of the application. This routine fetches the text from the text component and
    // writes it to the pipe so that the application can fetch it from the input stream.
    private void processCommand()
    {
        String lCommandRepr = getCmd();
        if (lCommandRepr.length() != 0) commandHistory.add(lCommandRepr);
        lCommandRepr = lCommandRepr + "\n";

        appendConsoleText("\n");
        commandHistoryIndex = 0;
        acceptLine(lCommandRepr);
        text.repaint();
    }

    private String getCmd()
    {
        try
        {
            return text.getText(commandPos, textLength() - commandPos);
        }
        catch (BadLocationException e)
        {
            return "";
        }
    }

    // Command history manipulation, go to the previous command.
    // Note that the index runs in reverse.
    private void prevHistory()
    {
        if (commandHistory.size() == 0) return;
        if (commandHistoryIndex == 0) currentCommand = getCmd();
        if (commandHistoryIndex < commandHistory.size())
        {
            commandHistoryIndex++;
            showHistory();
        }
    }

    // Command history manipulation, go to the next command.
    // Note that the index runs in reverse.
    private void nextHistory()
    {
        if (commandHistoryIndex == 0) return;
        commandHistoryIndex--;
        showHistory();
    }

    // Show the command from the command history, pointed to by the index.
    // Note that the index runs in reverse.
    private void showHistory()
    {
        String lShowLine;
        if (commandHistoryIndex == 0) lShowLine = currentCommand;
        else lShowLine = commandHistory.get(commandHistory.size() - commandHistoryIndex);

        replaceConsoleText(lShowLine, commandPos, textLength());
        text.setCaretPosition(textLength());
        text.repaint();
    }

    /** The user of the component can write a command in the console as if the user typed the
     *  command himself. The application using the console can simnulate user actions in this way.
     *
     * @param aCommand
     */
    public void setCommand(String aCommand)
    {
        String lCommandRepr = aCommand;

        if (lCommandRepr.length() != 0) commandHistory.add(lCommandRepr);
        lCommandRepr = lCommandRepr + "\n";

        appendConsoleText(lCommandRepr);
        commandHistoryIndex = 0;
        acceptLine(lCommandRepr);
        text.repaint();
    }

    // Put the text that the user typed into the pipe, so that
    // interested console clients can read the stuff from the in stream.
    private void acceptLine(String aLine)
    {
        try
        {
            fromConsoleStream.write(aLine.getBytes());
            fromConsoleStream.flush();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Clear the console.
     */
    public void clear()
    {
        text.setText("");
        text.repaint();
    }

    /**
     * Print output to the console. Note that this will not be interpreted as a command line.
     * See the setCommand() method for this functionality.
     *
     * @param aContent Print it to the console.
     */
    public void print(final Object aContent)
    {
        invokeAndWait(new Runnable()
        {
            public void run()
            {
                appendConsoleText(String.valueOf(aContent));
                initCommandPos();
                text.setCaretPosition(commandPos);
            }
        });
    }

    /**
     * Print error to the console. Note that this will not be interpreted as a command line.
     * See the setCommand() method for this functionality.
     *
     * @param aContent Print it to the console.
     */
    public void error(Object aContent)
    {
        print(aContent, attrError);
    }

    /**
     * Print output to the console. Note that this will not be interpreted as a command line.
     * See the setCommand() method for this functionality.
     *
     * @param aContent The message to be written to the console.
     * @param aAttribs The text attributes used for this message.
     */
    public void print(final Object aContent, final AttributeSet aAttribs)
    {
        invokeAndWait(new Runnable()
        {
            public void run()
            {
                final AttributeSet lOldAttribs = getStyle();
                setStyle(aAttribs, true);

                appendConsoleText(String.valueOf(aContent));
                initCommandPos();
                text.setCaretPosition(commandPos);

                setStyle(lOldAttribs, true);
            }
        });
    }

    private void setStyle(AttributeSet lAttribs, boolean lOverWrite)
    {
        text.setCharacterAttributes(lAttribs, lOverWrite);
    }

    private AttributeSet getStyle()
    {
        return text.getCharacterAttributes();
    }

    public void setFont(Font aFont)
    {
        super.setFont(aFont);
        if (text != null)  text.setFont(aFont);
    }

    // Utility method to make sure a task is executed on the Swing display thread.
    private void invokeAndWait(Runnable aRunnable)
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            try
            {
                SwingUtilities.invokeAndWait(aRunnable);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        else
        {
            aRunnable.run();
        }
    }

    private int textLength()
    {
        return text.getDocument().getLength();
    }

    // The PipePump waits for input on an output stream, and copies the content to the console window.
    // It is used to get the text from the pipes which are provided to the user of the console.
    // It is the connection between the user streams and the console content.
    private class PipePump
    implements Runnable
    {
        private InputStream in;
        private AttributeSet attr;

        public PipePump(InputStream aIn, AttributeSet aAttribs)
        {
            in = aIn;
            attr = aAttribs;
        }

        public void setAttr(AttributeSet aAttr)
        {
            attr = aAttr;
        }

        public void run()
        {
            try
            {
                final byte[] lBuf = new byte[1024];
                int lBytesRead;

                // The trick is to wait for some input on the stream and immediately acquire a lock on the console.
                // The first read operation does not read any data, it simply waits until data is available.
                // The pump starts working *after* the lock is acquired. We have at least two pumps, one for the
                // output and one for the errors. These two streams are competing for the console. Without acquiring
                // the lock, the stream with least data would mostly be the first one to gain the console.
                // Using the lock, the stream that was first written on by the user will mostly be the first
                // one getting the lock.
                while (in.read(lBuf, 0, 0) != -1)
                {
                    synchronized (JConsole.this)
                    {
                        while(in.available() > 0)
                        {
                            lBytesRead = in.read(lBuf);
                            print(new String(lBuf, 0, lBytesRead), attr);
                        }
                    }
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    private class MyKeyListener
    implements KeyListener
    {
        public void keyPressed(KeyEvent e)
        {
            type(e);
        }

        public void keyTyped(KeyEvent e)
        {
            type(e);
        }

        public void keyReleased(KeyEvent e)
        {
            type(e);
        }

        private synchronized void type(KeyEvent e)
        {
            switch (e.getKeyCode())
            {
                case (KeyEvent.VK_ENTER):
                    if (e.getID() == KeyEvent.KEY_PRESSED)
                    {
                        processCommand();
                        initCommandPos();
                        text.setCaretPosition(commandPos);
                    }
                    e.consume();
                    text.repaint();
                    break;
                case (KeyEvent.VK_UP):
                    if (e.getID() == KeyEvent.KEY_PRESSED) prevHistory();
                    e.consume();
                    break;

                case (KeyEvent.VK_DOWN):
                    if (e.getID() == KeyEvent.KEY_PRESSED) nextHistory();
                    e.consume();
                    break;
                case (KeyEvent.VK_LEFT):
                case (KeyEvent.VK_BACK_SPACE):
                case (KeyEvent.VK_DELETE):
                    if (text.getCaretPosition() <= commandPos) e.consume();
                    break;
                case (KeyEvent.VK_HOME):
                    text.setCaretPosition(commandPos);
                    e.consume();
                    break;
                case (KeyEvent.VK_U):
                    if ((e.getModifiers() & InputEvent.CTRL_MASK) > 0)
                    {
                        replaceConsoleText("", commandPos, textLength());
                        commandHistoryIndex = 0;
                        e.consume();
                    }
                    break;
                case (KeyEvent.VK_ALT):
                case (KeyEvent.VK_CAPS_LOCK):
                case (KeyEvent.VK_CONTROL):
                case (KeyEvent.VK_META):
                case (KeyEvent.VK_SHIFT):
                case (KeyEvent.VK_PRINTSCREEN):
                case (KeyEvent.VK_SCROLL_LOCK):
                case (KeyEvent.VK_PAUSE):
                case (KeyEvent.VK_INSERT):
                case (KeyEvent.VK_F1):
                case (KeyEvent.VK_F2):
                case (KeyEvent.VK_F3):
                case (KeyEvent.VK_F4):
                case (KeyEvent.VK_F5):
                case (KeyEvent.VK_F6):
                case (KeyEvent.VK_F7):
                case (KeyEvent.VK_F8):
                case (KeyEvent.VK_F9):
                case (KeyEvent.VK_F10):
                case (KeyEvent.VK_F11):
                case (KeyEvent.VK_F12):
                case (KeyEvent.VK_ESCAPE):
                case (KeyEvent.VK_C):
                    break;
                default:
                    if ((e.getModifiers() & (InputEvent.CTRL_MASK | InputEvent.ALT_MASK | InputEvent.META_MASK)) == 0)
                    {
                        moveCaret();
                    }

                    if ((e.paramString().contains("Backspace")) && (text.getCaretPosition() <= commandPos))
                    {
                        e.consume();
                    }
                    break;
            }
        }
    }

    private class MyMouseListener
    extends MouseAdapter
    {
        public void mousePressed(MouseEvent aEvent)
        {
            if (aEvent.isPopupTrigger())
                contextMenu.show((Component) aEvent.getSource(), aEvent.getX(), aEvent.getY());
        }

        public void mouseReleased(MouseEvent aEvent)
        {
            if (aEvent.isPopupTrigger())
                contextMenu.show((Component) aEvent.getSource(), aEvent.getX(), aEvent.getY());
        }
    }

    private class MyActionListener
    implements ActionListener
    {
        public void actionPerformed(ActionEvent aEvent)
        {
            final String lActionCommand = aEvent.getActionCommand();
            if (lActionCommand.equals(CMD_CUT))
            {
                text.cut();
            }
            else if (lActionCommand.equals(CMD_COPY))
            {
                text.copy();
            }
            else if (lActionCommand.equals(CMD_PASTE))
            {
                text.paste();
            }
        }
    }

    private class MyJTextPane
    extends JTextPane
    {
        public MyJTextPane() {super();}

        public void cut()
        {
            if (text.getCaretPosition() < commandPos) super.copy();
            else super.cut();
        }

        public void paste()
        {
            moveCaret();
            super.paste();
        }
    }
}