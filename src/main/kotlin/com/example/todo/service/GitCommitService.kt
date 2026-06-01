package com.example.todo.service

import com.intellij.openapi.project.Project
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class CommitInfo(
    val hash: String,
    val message: String,
    val author: String,
    val date: String
)

data class BranchCommits(
    val branch: String,
    val commits: List<CommitInfo>
)

class GitCommitService(private val project: Project) {

    fun getCurrentUserEmail(): String? {
        val dir = project.basePath?.let { File(it) } ?: return null
        return try {
            val proc = ProcessBuilder("git", "config", "user.email")
                .directory(dir)
                .redirectErrorStream(true)
                .start()
            val out = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            out.ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    fun getCommitsForRange(start: LocalDate, end: LocalDate, authorEmail: String? = null): List<BranchCommits> {
        val dir = project.basePath?.let { File(it) } ?: return emptyList()
        val branches = getBranches(dir)
        val after = start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " 00:00:00"
        val before = end.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " 23:59:59"

        return branches.mapNotNull { branch ->
            val commits = getBranchCommits(dir, branch, after, before, authorEmail)
            if (commits.isEmpty()) null else BranchCommits(branch, commits)
        }
    }

    private fun getBranches(dir: File): List<String> {
        return try {
            val proc = ProcessBuilder("git", "branch", "--format=%(refname:short)")
                .directory(dir)
                .redirectErrorStream(true)
                .start()
            val out = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            out.lines().filter { it.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun getBranchCommits(
        dir: File, branch: String, after: String, before: String, authorEmail: String?
    ): List<CommitInfo> {
        return try {
            val cmd = mutableListOf(
                "git", "log", branch,
                "--after=$after",
                "--before=$before",
                "--format=%H||%s||%an||%ai",
                "--reverse",
                "--no-merges"
            )
            if (authorEmail != null) {
                cmd.add("--author=$authorEmail")
            }
            val proc = ProcessBuilder(cmd)
                .directory(dir)
                .redirectErrorStream(true)
                .start()
            val out = proc.inputStream.bufferedReader().readText()
            proc.waitFor()

            out.lines().filter { it.isNotBlank() }.map { line ->
                val parts = line.split("||", limit = 4)
                CommitInfo(
                    hash = parts.getOrElse(0) { "" },
                    message = parts.getOrElse(1) { "" },
                    author = parts.getOrElse(2) { "" },
                    date = parts.getOrElse(3) { "" }
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
