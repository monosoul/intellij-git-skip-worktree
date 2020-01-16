package com.github.monosoul.git.updateindex.extended

import com.github.monosoul.git.updateindex.extended.ExtendedUpdateIndexCommand.MAKE_EXECUTABLE
import com.github.monosoul.git.updateindex.extended.ExtendedUpdateIndexCommand.MAKE_NOT_EXECUTABLE
import com.github.monosoul.git.updateindex.extended.ExtendedUpdateIndexCommand.NO_SKIP_WORKTREE
import com.github.monosoul.git.updateindex.extended.ExtendedUpdateIndexCommand.SKIP_WORKTREE
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.actions.AbstractVcsAction
import com.intellij.openapi.vcs.actions.VcsContext
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil.getVcsRootFor
import git4idea.commands.Git
import org.slf4j.LoggerFactory

sealed class AbstractExtendedUpdateIndexAction(private val command: ExtendedUpdateIndexCommand) : AbstractVcsAction() {

    private val logger = LoggerFactory.getLogger(AbstractExtendedUpdateIndexAction::class.java)

    override fun update(context: VcsContext, presentation: Presentation) {
        val vcsManager = context.project?.let(ProjectLevelVcsManager::getInstance)
        presentation.apply {
            if (vcsManager == null) {
                isEnabledAndVisible = false
            } else {
                isVisible = true
                isEnabled = !vcsManager.isBackgroundVcsOperationRunning
            }
        }
    }

    override fun actionPerformed(context: VcsContext) {
        context.apply {
            project?.run {
                selectedFiles
                        .mapNotNull { fileToVcsRoot(it) }
                        .groupBy({ it.second }, { it.first })
                        .apply {
                            map(gitLineHandlerCreator)
                                    .map(Git.getInstance()::runCommand)
                                    .filterNot { it.success() }
                                    .flatMap { it.errorOutput }
                                    .forEach(logger::error)
                        }
                        .values.flatten().forEach(vcsDirtyScopeManager::fileDirty)
            }
        }
    }

    internal val Project.gitLineHandlerCreator: GitLineHandlerCreator
        get() = GitLineHandlerCreatorImpl(this, command)

    private val Project.vcsDirtyScopeManager: VcsDirtyScopeManager
        get() = let(VcsDirtyScopeManager::getInstance)

    private fun Project.fileToVcsRoot(file: VirtualFile) = getVcsRootFor(this, file)?.let { file to it }
}

class MakeExecutableAction : AbstractExtendedUpdateIndexAction(MAKE_EXECUTABLE)

class MakeNotExecutableAction : AbstractExtendedUpdateIndexAction(MAKE_NOT_EXECUTABLE)

class NoSkipWorkTreeAction : AbstractExtendedUpdateIndexAction(NO_SKIP_WORKTREE)

class SkipWorkTreeAction : AbstractExtendedUpdateIndexAction(SKIP_WORKTREE)