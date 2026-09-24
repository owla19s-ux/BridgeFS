package com.owla19s.bridgefs
sealed class Command { data object ListTree:Command(); data class Read(val path:String):Command(); data class Write(val path:String,val content:String):Command(); data class Edit(val path:String,val old:String,val new:String):Command(); data class Search(val glob:String):Command(); data class Grep(val keyword:String):Command() }
object CommandParser {
 private val simple=Regex("(?m)^\\s*\\[(list)\\]\\s*$|^\\s*\\[(read|search|grep):\\s*(.*?)\\]\\s*$")
 private val write=Regex("(?s)```\\s*\\[write:\\s*(.+?)\\]\\s*\\n(.*?)\\[/write\\]\\s*```")
 private val edit=Regex("(?s)```\\s*\\[edit:\\s*(.+?)\\]\\s*\\n(.*?)\\[/edit\\]\\s*```")
 fun parse(input:String):List<Command>{ val h=mutableListOf<Pair<Int,Command>>(); simple.findAll(input).forEach{val s=it.value.trim();h+=it.range.first to when{ s=="[list]"->Command.ListTree;s.startsWith("[read:")->Command.Read(it.groupValues[3].trim());s.startsWith("[search:")->Command.Search(it.groupValues[3].trim());else->Command.Grep(it.groupValues[3].trim()) }};write.findAll(input).forEach{h+=it.range.first to Command.Write(it.groupValues[1].trim(),it.groupValues[2])};edit.findAll(input).forEach{val b=it.groupValues[2];val p=b.indexOf("====");if(p>=0)h+=it.range.first to Command.Edit(it.groupValues[1].trim(),b.substring(0,p),b.substring(p+4))};return h.sortedBy{it.first}.map{it.second} }
}
