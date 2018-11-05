import java.util.Collections

import spoon.Launcher
import spoon.processing.AbstractProcessor
import spoon.reflect.code._
import spoon.reflect.declaration._

object TransformStepsFromJava8ToJava {

  def main(args: Array[String]): Unit = {

    val launcher = new Launcher()
    launcher.getEnvironment.setAutoImports(true)
    val factory = launcher.createFactory()

    Launcher.main(Array(
      "--input", "example-input/",
      "--processors", "Java8StepToJavaStep",
      "--with-imports"

    ))
  }
}

class Java8StepToJavaStep extends AbstractProcessor[CtConstructor[_]] {
  override def process(element: CtConstructor[_]): Unit = {

    val clazz = element.getParent(classOf[CtClass[_]])
    val factory = element.getFactory

    element.getBody.forEach {
      case call: CtInvocation[_] if call.getTarget == null => () // super() constructor call
      case call: CtInvocation[_] =>

        val arguments = call.getArguments

        val (parameters, theBody) = arguments.get(1) match {
          case theLambda: CtLambda[_] =>
            (
              theLambda.getParameters,
              if (theLambda.getBody != null)
                theLambda.getBody
              else
                clone(theLambda.getExpression.asInstanceOf[CtStatement])
            )

          case methodRef: CtExecutableReferenceExpression[_,_] =>

            // This doesn't quite work, but it is somewhat close:
            (Collections.emptyList(),
              factory.createCodeSnippetStatement(methodRef + "()"))

          case x => throw new IllegalStateException("For " + x)
        }

        val method = factory.createMethod()

        val annotationType = factory.createTypeReference()
        annotationType.setPackage(factory.createPackageReference().setSimpleName("cucumber.api.java.en"))
        val stepTypeName = call.getExecutable.getSimpleName
        annotationType.setSimpleName(stepTypeName)
        val annotation = factory.createAnnotation()
        annotation.setAnnotationType(annotationType)
        val theStepLine = arguments.get(0).asInstanceOf[CtLiteral[_]]
        annotation.addValue("value", theStepLine)
        method.addAnnotation(annotation)

        method.addModifier(ModifierKind.PUBLIC)
        method.setType(factory.createTypeReference().setSimpleName("void"))
        method.setParameters(parameters.asInstanceOf[java.util.List[CtParameter[_]]])
        method.setBody(theBody)
        method.setSimpleName(
          stepTypeName.toLowerCase + "_" +
            theStepLine.getValue.toString
              .replace('-', '_')
              .replaceAll("[^\\w ]", "")
              .replace(' ', '_'))

        clazz.addTypeMember(method)

        ()

      case x => println("IGNORING: " + x)
    }

    element.setBody(element.getFactory.createBlock())
  }

  // See https://issues.scala-lang.org/browse/SI-151
  def clone(x: CtStatement): CtStatement = {
    classOf[CtStatement].getMethod("clone")
      .invoke(x)
      .asInstanceOf[CtStatement]
  }
}
