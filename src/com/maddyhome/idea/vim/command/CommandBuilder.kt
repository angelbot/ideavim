package com.maddyhome.idea.vim.command

import com.maddyhome.idea.vim.handler.EditorActionHandlerBase
import com.maddyhome.idea.vim.key.CommandPartNode
import com.maddyhome.idea.vim.key.Node
import com.maddyhome.idea.vim.key.RootNode
import java.util.*
import javax.swing.KeyStroke

class CommandBuilder(private var currentCommandPartNode: CommandPartNode) {
  private val commandParts = Stack<Command>()
  private var keys = mutableListOf<KeyStroke>()

  var commandState = CurrentCommandState.NEW_COMMAND
  var expectedArgumentType: Argument.Type? = null
  var count = 0
    private set

  val isReady get() = commandState == CurrentCommandState.READY
  val isBad get() = commandState == CurrentCommandState.BAD_COMMAND
  val isEmpty get() = commandParts.empty()
  val isAtDefaultState get() = isEmpty && count == 0 && expectedArgumentType == null

  fun pushCommandPart(action: EditorActionHandlerBase) {
    commandParts.push(Command(count, action, action.type, action.flags, keys))
    keys = mutableListOf()
  }

  fun addKey(keyStroke: KeyStroke) {
    keys.add(keyStroke)
  }

  fun addCountCharacter(chKey: Char) {
    count = (count * 10) + (chKey - '0')
  }

  fun deleteCountCharacter() {
    count /= 10
  }

  fun setCurrentCommandPartNode(newNode: CommandPartNode) {
    currentCommandPartNode = newNode
  }

  fun getChildNode(key: KeyStroke): Node? {
    return currentCommandPartNode[key]
  }

  fun isBuildingMultiKeyCommand(): Boolean {
    // Don't apply mapping if we're in the middle of building a multi-key command.
    // E.g. given nmap s v, don't try to map <C-W>s to <C-W>v
    //   Similarly, nmap <C-W>a <C-W>s should not try to map the second <C-W> in <C-W><C-W>
    // Note that we might still be at RootNode if we're handling a prefix, because we might be buffering keys until we
    // get a match. This means we'll still process the rest of the keys of the prefix.
    return currentCommandPartNode !is RootNode
  }

  fun setCurrentCommandPartArgument(argument: Argument) {
    commandParts.peek().argument = argument
  }

  fun getLastCommandPartAction(): EditorActionHandlerBase? {
    return commandParts.peek()?.action
  }

  fun hasCurrentCommandPartArgument(): Boolean {
    return commandParts.peek()?.argument != null
  }

  fun peekCurrentCommandPartArgumentType(): Argument.Type? {
    return commandParts.peek()?.argument?.type
  }

  fun replaceCurrentCommandPart(action: EditorActionHandlerBase, argument: Argument) {
    popCommandPart()
    pushCommandPart(action)
    setCurrentCommandPartArgument(argument)
  }

  fun popCommandPart() {
    commandParts.pop()
  }

  fun buildCommand(): Command {
    /* Let's go through the command stack and merge it all into one command. At this time there should never
       be more than two commands on the stack - one is the actual command, and the other would be a motion
       command argument needed by the first command */
    var command: Command = commandParts.pop()
    while (commandParts.size > 0) {
      val top: Command = commandParts.pop()
      top.argument = Argument(command)
      command = top
    }
    return command
  }

  fun resetAll(commandPartNode: CommandPartNode) {
    resetInProgressCommandPart(commandPartNode)
    commandState = CurrentCommandState.NEW_COMMAND
    commandParts.clear()
    expectedArgumentType = null
  }

  fun resetInProgressCommandPart(commandPartNode: CommandPartNode) {
    keys.clear()
    count = 0
    setCurrentCommandPartNode(commandPartNode)
  }
}