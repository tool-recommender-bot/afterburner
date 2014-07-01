package com.github.stephanenicolas.afterburner;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;

import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

import com.github.stephanenicolas.afterburner.exception.AfterBurnerImpossibleException;
import com.github.stephanenicolas.afterburner.inserts.InsertableConstructor;
import com.github.stephanenicolas.afterburner.inserts.InsertableMethod;

public class AfterBurnerTest {

    private AfterBurner afterBurner;
    private CtClass target;
    private Class<?> targetClass;
    private Object targetInstance;

    @Before
    public void setUp() {
        afterBurner = new AfterBurner(EasyMock.createNiceMock(Logger.class));
        target = ClassPool.getDefault().makeClass(
                "Target" + TestCounter.testCounter);
        TestCounter.testCounter++;
    }

    @Test
    public void testAddMethod() throws Exception {
        // GIVEN
        InsertableMethod insertableMethod = new SimpleInsertableMethod(target,
                "foo", "public boolean foo() { return true; }", null, null, null);

        // WHEN
        afterBurner.addOrInsertMethod(insertableMethod);

        // THEN
        targetClass = target.toClass();
        targetInstance = targetClass.newInstance();
        assertHasFooMethodWithReturnValue(target, true);
    }

    @Test
    public void testInsertMethod_after() throws Exception {
        // GIVEN
        target.addMethod(CtNewMethod.make("public void bar() { }", target));
        target.addMethod(CtNewMethod.make("public boolean foo() { bar(); return false; }", target));
        target.addField(new CtField(CtClass.intType, "foo", target));
        InsertableMethod insertableMethod = new SimpleInsertableMethod(target,
                "foo", null, "foo = 2;", null, "bar");

        // WHEN
        afterBurner.addOrInsertMethod(insertableMethod);

        // THEN
        targetClass = target.toClass();
        targetInstance = targetClass.newInstance();
        assertHasFooMethodWithReturnValue(target, false);
        assertHasFooFieldWithValue(target, 2);
    }

    @Test
    public void testInsertMethod_before() throws Exception {
        // GIVEN
        target.addMethod(CtNewMethod.make("public void bar() { }", target));
        target.addMethod(CtNewMethod.make("public boolean foo() { bar(); return false; }", target));
        target.addField(new CtField(CtClass.intType, "foo", target));
        InsertableMethod insertableMethod = new SimpleInsertableMethod(target,
                "foo", null, "foo = 2;", "bar", null);

        // WHEN
        afterBurner.addOrInsertMethod(insertableMethod);

        // THEN
        targetClass = target.toClass();
        targetInstance = targetClass.newInstance();
        assertHasFooMethodWithReturnValue(target, false);
        assertHasFooFieldWithValue(target, 2);
    }

    @Test(expected=AfterBurnerImpossibleException.class)
    public void testInsertMethod_not_before_not_after() throws Exception {
        // GIVEN
        target.addMethod(CtNewMethod.make("public void bar() { }", target));
        target.addMethod(CtNewMethod.make("public boolean foo() { bar(); return false; }", target));
        target.addField(new CtField(CtClass.intType, "foo", target));
        InsertableMethod insertableMethod = new SimpleInsertableMethod(target,
                "foo", null, "foo = 2;", null, null);

        // WHEN
        afterBurner.addOrInsertMethod(insertableMethod);

        // THEN
        fail();
    }

    @Test
    public void testInsertConstructor() throws Exception {
        // GIVEN
        target.addConstructor(CtNewConstructor.make("public Target() {}", target));
        target.addField(new CtField(CtClass.intType, "foo", target));
        InsertableConstructor insertableConstructor = new SimpleInsertableConstructor(target,
                "foo = 2;");

        // WHEN
        afterBurner.insertConstructor(insertableConstructor);

        // THEN
        targetClass = target.toClass();
        targetInstance = targetClass.newInstance();
        assertHasFooFieldWithValue(target, 2);
    }

    @Test(expected=AfterBurnerImpossibleException.class)
    public void testInsertConstructor_with_no_constructor() throws Exception {
        // GIVEN
        target.addConstructor(CtNewConstructor.make("public Target() {}", target));
        target.addField(new CtField(CtClass.intType, "foo", target));
        InsertableConstructor insertableConstructor = new SimpleInsertableConstructor(target,
                "foo = 2;") {
            @Override
            public boolean acceptParameters(CtClass[] paramClasses) throws AfterBurnerImpossibleException {
                return false;
            };
        };

        // WHEN
        afterBurner.insertConstructor(insertableConstructor);

        // THEN
        fail();
    }

    @Test
    public void testBeforeOverride() throws Exception {
        // GIVEN
        target.addMethod(CtNewMethod.make("public void foo() { }", target));
        afterBurner = EasyMock.createMockBuilder(AfterBurner.class).withConstructor(EasyMock.createNiceMock(Logger.class)).addMockedMethod("addOrInsertMethod").createMock();
        Capture<InsertableMethod> captured = new Capture<InsertableMethod>();
        afterBurner.addOrInsertMethod(EasyMock.capture(captured));
        EasyMock.expectLastCall().once();
        EasyMock.replay(afterBurner);

        String targetMethodName = "foo";
        String body = "foo = 2;";

        // WHEN
        afterBurner.beforeOverrideMethod(target, targetMethodName, body);

        // THEN
        EasyMock.verify(afterBurner);
        assertEquals(body, captured.getValue().getBody());
        assertEquals(targetMethodName, captured.getValue().getInsertionBeforeMethod());
        assertNull(captured.getValue().getInsertionAfterMethod());
        assertEquals(target, captured.getValue().getClassToInsertInto());
    }

    @Test
    public void testAfterOverride() throws Exception {
        // GIVEN
        target.addMethod(CtNewMethod.make("public void foo() { }", target));
        afterBurner = EasyMock.createMockBuilder(AfterBurner.class).withConstructor(EasyMock.createNiceMock(Logger.class)).addMockedMethod("addOrInsertMethod").createMock();
        Capture<InsertableMethod> captured = new Capture<InsertableMethod>();
        afterBurner.addOrInsertMethod(EasyMock.capture(captured));
        EasyMock.expectLastCall().once();
        EasyMock.replay(afterBurner);

        String targetMethodName = "foo";
        String body = "foo = 2;";

        // WHEN
        afterBurner.afterOverrideMethod(target, targetMethodName, body);

        // THEN
        EasyMock.verify(afterBurner);
        assertEquals(body, captured.getValue().getBody());
        assertNull(captured.getValue().getInsertionBeforeMethod());
        assertEquals(targetMethodName, captured.getValue().getInsertionAfterMethod());
        assertEquals(target, captured.getValue().getClassToInsertInto());
    }

    private void assertHasFooMethodWithReturnValue(CtClass clazz, boolean returnValue) throws Exception {
        CtMethod fooMethod = clazz.getDeclaredMethod("foo");
        assertNotNull(fooMethod);
        // we also need to check if code has been copied

        Method realFooMethod = targetInstance.getClass().getMethod("foo");
        assertEquals(returnValue, realFooMethod.invoke(targetInstance));
    }

    private void assertHasFooFieldWithValue(CtClass clazz, int value) throws Exception {
        CtField fooField = clazz.getField("foo");
        assertNotNull(fooField);
        CtClass fooFieldType = fooField.getType();
        assertEquals(CtClass.intType, fooFieldType);

        Field realFooField = targetInstance.getClass().getDeclaredField("foo");
        realFooField.setAccessible(true);
        assertEquals(value, realFooField.get(targetInstance));
    }

    private final class SimpleInsertableMethod extends InsertableMethod {
        private String targetMethodName;
        private String fullMethod;
        private String body;
        private String insertionBeforeMethod;
        private String insertionAfterMethod;

        public SimpleInsertableMethod(CtClass classToInsertInto,
                String targetMethodName, String fullMethod, String body, String insertionBeforeMethod, String insertionAfterMethod) {
            super(classToInsertInto);
            this.targetMethodName = targetMethodName;
            this.fullMethod = fullMethod;
            this.body = body;
            this.insertionBeforeMethod = insertionBeforeMethod;
            this.insertionAfterMethod = insertionAfterMethod;
        }

        @Override
        public String getTargetMethodName() throws AfterBurnerImpossibleException {
            return targetMethodName;
        }

        @Override
        public String getFullMethod() throws AfterBurnerImpossibleException {
            return fullMethod;
        }

        @Override
        public String getBody() throws AfterBurnerImpossibleException {
            return body;
        }

        @Override
        public String getInsertionBeforeMethod() {
            return insertionBeforeMethod;
        }

        @Override
        public String getInsertionAfterMethod() {
            return insertionAfterMethod;
        }
    }

    public class SimpleInsertableConstructor extends InsertableConstructor {
        private String body;

        public SimpleInsertableConstructor(CtClass classToInsertInto, String body) {
            super(classToInsertInto);
            this.body = body;
        }

        @Override
        public String getConstructorBody(CtClass[] paramClasses) throws AfterBurnerImpossibleException {
            return body;
        }

        @Override
        public boolean acceptParameters(CtClass[] paramClasses) throws AfterBurnerImpossibleException {
            return true;
        }
    }

}
