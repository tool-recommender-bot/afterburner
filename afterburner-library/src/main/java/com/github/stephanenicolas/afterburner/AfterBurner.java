package com.github.stephanenicolas.afterburner;

import java.util.ArrayList;
import java.util.List;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

import com.github.stephanenicolas.afterburner.exception.AfterBurnerImpossibleException;
import com.github.stephanenicolas.afterburner.inserts.InsertableConstructor;
import com.github.stephanenicolas.afterburner.inserts.InsertableMethod;
import com.github.stephanenicolas.afterburner.inserts.CtMethodJavaWriter;
import lombok.extern.slf4j.Slf4j;

/**
 * Allows to modify byte code of java classes via javassist.
 * This class allows a rich API to injeect byte code into methods or constructors of a given class.
 * @author SNI
 */
@Slf4j
public class AfterBurner {
    private CtMethodJavaWriter signatureExtractor;

    public AfterBurner() {
        signatureExtractor = new CtMethodJavaWriter();
    }

    /**
     * Add/Inserts java instructions into a given method of a given class.
     * @param insertableMethod contains all information to perform byte code injection.
     * @throws CannotCompileException if the source contained in insertableMethod can't be compiled.
     * @throws AfterBurnerImpossibleException if something else goes wrong, wraps other exceptions.
     */
    public void addOrInsertMethod(InsertableMethod insertableMethod) throws CannotCompileException, AfterBurnerImpossibleException {
        log.info("InsertableMethod : " + insertableMethod);
        // create or complete onViewCreated
        String targetMethodName = insertableMethod.getTargetMethodName();
        CtClass classToTransform = insertableMethod.getClassToInsertInto();
        CtMethod targetMethod = extractExistingMethod(classToTransform,
                targetMethodName);
        log.info("Method : " + targetMethod);
        if (targetMethod != null) {
            InsertableMethodInjectorEditor injectorEditor = new InsertableMethodInjectorEditor(
                    classToTransform, insertableMethod);
            targetMethod.instrument(injectorEditor);
            if (!injectorEditor.isSuccessful) {
                throw new CannotCompileException("Transformation failed. Insertion method not found.: " + targetMethodName);
            }
        } else {
            classToTransform.addMethod(CtNewMethod.make(
                    insertableMethod.getFullMethod(), classToTransform));
        }
    }

    /**
     * Add/Inserts java instructions into a given override method of a given class. Before the overriden method call.
     * @param targetClass the class to inject code into.
     * @param targetMethodName the method to inject code into. Body will be injected right before the call to super.&lt;targetName&gt;.
     * @param body the instructions of java to be injected.
     * @throws CannotCompileException if the source contained in insertableMethod can't be compiled.
     * @throws AfterBurnerImpossibleException if something else goes wrong, wraps other exceptions.
     */
    public void beforeOverrideMethod(CtClass targetClass, String targetMethodName, String body) throws CannotCompileException, AfterBurnerImpossibleException, NotFoundException {
        InsertableMethod insertableMethod = new InsertableMethodBuilder(this, signatureExtractor).insertIntoClass(targetClass).beforeOverrideMethod(targetMethodName).withBody(body).createInsertableMethod();
        addOrInsertMethod(insertableMethod);
    }

    /**
     * Add/Inserts java instructions into a given override method of a given class. After the overriden method call.
     * @param targetClass the class to inject code into.
     * @param targetMethodName the method to inject code into. Body will be injected right after the call to super.&lt;targetName&gt;.
     * @param body the instructions of java to be injected.
     * @throws CannotCompileException if the source contained in insertableMethod can't be compiled.
     * @throws AfterBurnerImpossibleException if something else goes wrong, wraps other exceptions.
     */
    public void afterOverrideMethod(CtClass targetClass, String targetMethodName, String body) throws CannotCompileException, AfterBurnerImpossibleException, NotFoundException {
        InsertableMethod insertableMethod = new InsertableMethodBuilder(this, signatureExtractor).insertIntoClass(targetClass).afterOverrideMethod(targetMethodName).withBody(body).createInsertableMethod();
        addOrInsertMethod(insertableMethod);
    }


    /**
     * Inserts java instructions into all constructors a given class.
     * @param insertableConstructor contains all information about insertion.
     * @throws CannotCompileException if the source contained in insertableMethod can't be compiled.
     * @throws AfterBurnerImpossibleException if something else goes wrong, wraps other exceptions.
     */
    public void insertConstructor(InsertableConstructor insertableConstructor) throws CannotCompileException, AfterBurnerImpossibleException,
    NotFoundException {
        // create or complete onViewCreated
        List<CtConstructor> constructorList = extractExistingConstructors(insertableConstructor);
        log.info("constructor : " + constructorList.toString());
        if (!constructorList.isEmpty()) {
            for (CtConstructor constructor : constructorList) {
                constructor
                .insertBeforeBody(insertableConstructor
                        .getConstructorBody(constructor
                                .getParameterTypes()));
            }
        } else {
            throw new AfterBurnerImpossibleException("No suitable constructor was found in class " + insertableConstructor.getClassToInsertInto().getName() + ". Add a constructor that is accepted by the InsertableConstructor. Don't use non static inner classes.");
        }
    }

    /**
     * Returns the method named {@code methodName} in {@code classToTransform}. Null if not found.
     * Due to limitations of javassist, in case of multiple overloads, one of them only is returned.
     * (https://github.com/jboss-javassist/javassist/issues/9)
     * @param classToTransform the class that should contain a method methodName.
     * @param methodName the name of the method to retrieve.
     * @return the method named {@code methodName} in {@code classToTransform}. Null if not found.
     */
    public CtMethod extractExistingMethod(final CtClass classToTransform,
            String methodName) {
        try {
            return classToTransform.getDeclaredMethod(methodName);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean checkIfMethodIsInvoked(CtMethod withinMethod,
        String invokedMethod) throws CannotCompileException {
        return new DetectMethodCallEditor(withinMethod, invokedMethod).checkIfisCallingMethod();
    }

    private List<CtConstructor> extractExistingConstructors(final InsertableConstructor insertableConstructor) throws NotFoundException, AfterBurnerImpossibleException {
        List<CtConstructor> constructors = new ArrayList<CtConstructor>();
        CtConstructor[] declaredConstructors = insertableConstructor
                .getClassToInsertInto().getDeclaredConstructors();
        for (CtConstructor constructor : declaredConstructors) {
            CtClass[] paramClasses = constructor.getParameterTypes();
            if (insertableConstructor.acceptParameters(paramClasses)) {
                constructors.add(constructor);
            }
        }
        return constructors;
    }

    private static final class InsertableMethodInjectorEditor extends ExprEditor {
        private final CtClass classToTransform;
        private final String insertionMethod;
        private final boolean insertAfter;
        private final String bodyToInsert;
        private boolean isSuccessful;

        private InsertableMethodInjectorEditor(CtClass classToTransform, InsertableMethod insertableMethod) throws AfterBurnerImpossibleException {
            this.classToTransform = classToTransform;
            String insertionAfterMethod = insertableMethod
                    .getInsertionAfterMethod();
            String insertionBeforeMethod = insertableMethod
                    .getInsertionBeforeMethod();
            if (insertionBeforeMethod == null && insertionAfterMethod == null) {
                throw new AfterBurnerImpossibleException(
                        "Error in class "
                                + insertableMethod.getClass()
                                + " both insertionBeforeMethod && insertionAfterMethod are null.");
            } else if (insertionBeforeMethod != null) {
                insertionMethod = insertionBeforeMethod;
                insertAfter = false;
            } else {
                insertionMethod = insertionAfterMethod;
                insertAfter = true;
            }
            bodyToInsert = insertableMethod.getBody();
        }

        @Override
        public void edit(MethodCall m) throws CannotCompileException {
            if (m.getMethodName().equals(insertionMethod)) {

                String origMethodCall = "$_ = $proceed($$);;\n";
                if (insertAfter) {
                    origMethodCall = origMethodCall + bodyToInsert;
                } else {
                    origMethodCall = bodyToInsert + origMethodCall;
                }

                log.info("Injected : " + origMethodCall);
                log.info("Class " + classToTransform.getName() + " has been enhanced.");
                m.replace(origMethodCall);
                isSuccessful = true;
            }
        }
    }

    private static final class DetectMethodCallEditor extends ExprEditor {

        private CtMethod withinMethod;
        private String methodName;
        private boolean isCallingMethod;

        private DetectMethodCallEditor(CtMethod withinMethod, String methodName) {
            this.withinMethod = withinMethod;
            this.methodName = methodName;
        }

        @Override
        public void edit(MethodCall m) throws CannotCompileException {
            if (m.getMethodName().equals(methodName)) {
                this.isCallingMethod = true;
            }
        }

        public boolean checkIfisCallingMethod() throws CannotCompileException {
            withinMethod.instrument(this);
            return isCallingMethod;
        }
    }
}
