import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldingModel
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import liveplugin.*
import liveplugin.PluginUtil.*
import liveplugin.implementation.Editors.registerEditorListener

val FOLD_ON_OPEN = "FOLD_ON_OPEN"
val FOLD_ON_OPEN_DEFAULT = true
fun foldOnOpen(): Boolean = getGlobalVar<Boolean>(FOLD_ON_OPEN) ?: FOLD_ON_OPEN_DEFAULT

val fileExtensions = listOf("jsx", "tsx", "html")
val attributeNamesToFold = listOf("className", "data-hello", "name=")
val foldedText = "className"

sealed interface ReplaceStrategy {
  object ByFoldedText : ReplaceStrategy
  object ByAttributeName : ReplaceStrategy
}

val replaceStrategy: ReplaceStrategy = ReplaceStrategy.ByAttributeName

data class FoldRegion(val start: Int, val end: Int, val name: String)

val FoldAllClassName: AnAction = createFoldingAction("Fold all className", true)
val UnfoldAllClassName: AnAction = createFoldingAction("Unfold all className", false)

registerEditorListener(pluginDisposable, object : FileEditorManagerListener {
  override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
    super.fileOpened(source, file)

    val contains = fileExtensions.contains(file.extension)
    val foldOnOpen = foldOnOpen()
    if (contains && foldOnOpen) {
      val editor: Editor = project?.currentEditor ?: return
      // dirty hack, read method documentation
      val project = currentProjectInFrame() ?: return;
      val psiFile: PsiFile = psiFile(file, project) ?: return;

      foldRegions(psiFile, editor, true)
    }
  }
})

fun foldRegions(psiFile: PsiFile, editor: Editor, fold: Boolean) {
  val regionsToFold = getRegionsToFold(psiFile)

  val foldingModel: FoldingModel = editor.foldingModel
  doFolding(foldingModel, regionsToFold, fold)
}

registerAction(id = "Show folding menu", keyStroke = "ctrl shift W", disposable = pluginDisposable) { event: AnActionEvent ->
  val project = event.project ?: return@registerAction
  val editor: Editor = event.editor ?: return@registerAction
  event.editor?.document

  val foldOnOpen: Boolean = getGlobalVar<Boolean>(FOLD_ON_OPEN) ?: FOLD_ON_OPEN_DEFAULT

  val actionGroup = PopupActionGroup(
          "XML parameter folder",
          FoldAllClassName,
          UnfoldAllClassName,
          AnAction("Toggle fold class name on open: $foldOnOpen") {
            setGlobalVar<Boolean>(FOLD_ON_OPEN, !foldOnOpen)
          },
  )
  actionGroup.createPopup(event.dataContext).showCenteredInCurrentWindow(project)
}


//fun setClassNameFolding(editor: Editor, expand: Boolean) {
//  val text = editor.document.text
//  val foldingModel = editor.foldingModel
//  val paramName = "className"
//
//  val allClassNames: Sequence<Int> = text.allOccurrencesOf(paramName)
//  foldingModel.runBatchFoldingOperation {
//    allClassNames.forEach { classNameStart ->
//      val char = text[classNameStart + paramName.length + 1]
//      val closingChar = when (char) {
//        '"' -> '"'
//        '\'' -> '\''
//        '{' -> '}'
//        else -> char
//      }
//
//      val classNameValueStart = text.indexOf(char, classNameStart + paramName.length)
//      val classNameValueEnd = text.indexOf(closingChar, classNameValueStart + 1)
//
//      val regionStart = classNameStart
//      val regionEnd = classNameValueEnd + 1
//
//      val foldingRegin = foldingModel.getFoldRegion(regionStart, regionEnd)
//              ?: foldingModel.addFoldRegion(regionStart, regionEnd, foldedText)
//
//      if (foldingRegin !== null) {
//        foldingRegin.isExpanded = expand
//      }
//    }
//  }
//}

fun doFolding(foldingModel: FoldingModel, regionsToFold: MutableList<FoldRegion>, fold: Boolean) {
  foldingModel.runBatchFoldingOperation {
    regionsToFold.forEach {
      val text: String = when (replaceStrategy) {
        is ReplaceStrategy.ByFoldedText -> foldedText
        is ReplaceStrategy.ByAttributeName -> it.name
        else -> throw java.lang.IllegalStateException("replaceStrategy else branch!")
      }

      val foldingRegin = foldingModel.getFoldRegion(it.start, it.end)
              ?: foldingModel.addFoldRegion(it.start, it.end, text)

      if (foldingRegin !== null) {
        foldingRegin.isExpanded = !fold
      }
    }
  }
}

fun getRegionsToFold(psiFile: PsiFile): MutableList<Plugin.FoldRegion> {
  val regionsToFold = mutableListOf<Plugin.FoldRegion>()

  psiFile.accept(object : PsiRecursiveElementWalkingVisitor() {
    override fun visitElement(element: PsiElement) {
      super.visitElement(element)

      // todo refactor me
      attributeNamesToFold.forEach {
        val startsWith = element.text.startsWith(it) && !element.text.endsWith(it)
        if (startsWith) {
          regionsToFold.add(FoldRegion(
                  start = element.textRange.startOffset,
                  end = element.textRange.endOffset,
                  name = it
          ))
        }
      }
      // zaczyna się od className, ale sie nim nie konczy, czyli to jest ten cały, okalajacy element

    }
  })
  return regionsToFold
}

fun createFoldingAction(name: String, fold: Boolean) = AnAction(name) {
  val editor: Editor = it.editor ?: return@AnAction
  val psiFile: PsiFile = it.psiFile ?: return@AnAction;

  foldRegions(psiFile, editor, fold)
}

if (!isIdeStartup) show("Folding3 loaded!")

//registerEditorListener(pluginDisposable, object : FileEditorManagerListener {
//  override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
//    super.fileOpened(source, file)
//
//    val contains = fileExtensions.contains(file.extension)
//    val foldOnOpen = foldOnOpen()
//    if (contains && foldOnOpen) {
//      // dirty hack, read method documentation
//      val project = currentProjectInFrame() ?: return;
//      val psiFile: PsiFile = psiFile(file, project) ?: return;
//      psiFile.accept(object : PsiRecursiveElementWalkingVisitor() {
//        override fun visitElement(element: PsiElement) {
//          super.visitElement(element)
//          element.text.startsWith("className")
//        }
//      })
//
//      val editor = project.currentEditor ?: return;
//
//      setClassNameFolding(editor, false)
//    }
//  }
//})

