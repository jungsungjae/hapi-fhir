package ca.uhn.fhir.interceptor.executor;

import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.HookParams;
import ca.uhn.fhir.interceptor.api.IAnonymousInterceptor;
import ca.uhn.fhir.interceptor.api.IPointcut;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class InterceptorServiceTest {

	private final List<String> myInvocations = new ArrayList<>();

	@Test
	public void testInterceptorWithAnnotationDefinedOnInterface() {

		InterceptorService svc = new InterceptorService();
		TestInterceptorWithAnnotationDefinedOnInterface_Class interceptor = new TestInterceptorWithAnnotationDefinedOnInterface_Class();
		svc.registerInterceptor(interceptor);

		assertEquals(1, interceptor.getRegisterCount());
	}

	@Test
	public void testInterceptorThrowsException() {

		class InterceptorThrowingException {
			@Hook(Pointcut.TEST_RB)
			public void test(String theValue) {
				throw new AuthenticationException(theValue);
			}
		}

		InterceptorService svc = new InterceptorService();
		svc.registerInterceptor(new InterceptorThrowingException());

		try {
			svc.callHooks(Pointcut.TEST_RB, new HookParams("A MESSAGE", "B"));
			fail();
		} catch (AuthenticationException e) {
			assertEquals("A MESSAGE", e.getMessage());
		}

	}

	@Test
	public void testInterceptorReturnsClass() {

		class InterceptorReturningClass {

			private BaseServerResponseException myNextResponse;

			@Hook(Pointcut.TEST_RO)
			public BaseServerResponseException hook() {
				return myNextResponse;
			}

		}

		InterceptorReturningClass interceptor0 = new InterceptorReturningClass();
		InterceptorReturningClass interceptor1 = new InterceptorReturningClass();

		InterceptorService svc = new InterceptorService();
		svc.registerInterceptor(interceptor0);
		svc.registerInterceptor(interceptor1);

		interceptor0.myNextResponse = new InvalidRequestException("0");
		interceptor1.myNextResponse = new InvalidRequestException("1");
		Object response = svc.callHooksAndReturnObject(Pointcut.TEST_RO, new HookParams("", ""));
		assertEquals("0", ((InvalidRequestException) response).getMessage());

		interceptor0.myNextResponse = null;
		response = svc.callHooksAndReturnObject(Pointcut.TEST_RO, new HookParams("", ""));
		assertEquals("1", ((InvalidRequestException) response).getMessage());
	}


	/**
	 * Hook methods with private access are ignored
	 */
	@Test
	public void testInterceptorWithPrivateAccessHookMethod() {

		class InterceptorThrowingException {
			@Hook(Pointcut.TEST_RB)
			private void test(String theValue) {
				throw new AuthenticationException(theValue);
			}
		}

		InterceptorService svc = new InterceptorService();
		svc.registerInterceptor(new InterceptorThrowingException());

		// Should not fail
		svc.callHooks(Pointcut.TEST_RB, new HookParams("A MESSAGE", "B"));
	}

	@Test
	public void testInterceptorWithDefaultAccessHookMethod() {

		class InterceptorThrowingException {
			@Hook(Pointcut.TEST_RB)
			void test(String theValue) {
				throw new AuthenticationException(theValue);
			}
		}

		InterceptorService svc = new InterceptorService();
		svc.registerInterceptor(new InterceptorThrowingException());

		try {
			svc.callHooks(Pointcut.TEST_RB, new HookParams("A MESSAGE", "B"));
			fail();
		} catch (AuthenticationException e) {
			assertEquals("A MESSAGE", e.getMessage());
		}

	}

	@Test
	public void testInterceptorWithInheritedHookMethod() {

		class InterceptorThrowingException {
			@Hook(Pointcut.TEST_RB)
			void test(String theValue) {
				throw new AuthenticationException(theValue);
			}
		}

		class InterceptorThrowingException2 extends InterceptorThrowingException {
			// nothing
		}

		InterceptorService svc = new InterceptorService();
		svc.registerInterceptor(new InterceptorThrowingException2());

		try {
			svc.callHooks(Pointcut.TEST_RB, new HookParams("A MESSAGE", "B"));
			fail();
		} catch (AuthenticationException e) {
			assertEquals("A MESSAGE", e.getMessage());
		}

	}

	@Test
	public void testInterceptorWithNoHooks() {

		class InterceptorWithNoHooks {
			// nothing
		}

		InterceptorService svc = new InterceptorService();
		svc.setWarnOnInterceptorWithNoHooks(false);
		boolean outcome = svc.registerInterceptor(new InterceptorWithNoHooks());
		assertFalse(outcome);
	}

	@Test
	public void testRegisterHookFails() {
		InterceptorService svc = new InterceptorService();
		int initialSize = svc.getGlobalInterceptorsForUnitTest().size();

		try {
			svc.registerInterceptor(new InterceptorThatFailsOnRegister());
			fail();
		} catch (InternalErrorException e) {
			// good
		}

		assertEquals(initialSize, svc.getGlobalInterceptorsForUnitTest().size());

	}

	@Test
	public void testManuallyRegisterInterceptor() {
		InterceptorService svc = new InterceptorService();

		// Registered in opposite order to verify that the order on the annotation is used
		MyTestInterceptorTwo interceptor1 = new MyTestInterceptorTwo();
		MyTestInterceptorOne interceptor0 = new MyTestInterceptorOne();
		assertFalse(svc.hasHooks(Pointcut.TEST_RB));
		svc.registerInterceptor(interceptor1);
		assertTrue(svc.hasHooks(Pointcut.TEST_RB));
		svc.registerInterceptor(interceptor0);
		assertTrue(svc.hasHooks(Pointcut.TEST_RB));

		// Register the manual interceptor (has Order right in the middle)
		MyTestInterceptorManual myInterceptorManual = new MyTestInterceptorManual();
		svc.registerInterceptor(myInterceptorManual);
		List<Object> globalInterceptors = svc.getGlobalInterceptorsForUnitTest();
		assertEquals(3, globalInterceptors.size());
		assertTrue(globalInterceptors.get(0) instanceof MyTestInterceptorOne, globalInterceptors.get(0).getClass().toString());
		assertTrue(globalInterceptors.get(1) instanceof MyTestInterceptorManual, globalInterceptors.get(1).getClass().toString());
		assertTrue(globalInterceptors.get(2) instanceof MyTestInterceptorTwo, globalInterceptors.get(2).getClass().toString());

		// Try to register again (should have no effect
		svc.registerInterceptor(myInterceptorManual);
		globalInterceptors = svc.getGlobalInterceptorsForUnitTest();
		assertEquals(3, globalInterceptors.size());
		assertTrue(globalInterceptors.get(0) instanceof MyTestInterceptorOne, globalInterceptors.get(0).getClass().toString());
		assertTrue(globalInterceptors.get(1) instanceof MyTestInterceptorManual, globalInterceptors.get(1).getClass().toString());
		assertTrue(globalInterceptors.get(2) instanceof MyTestInterceptorTwo, globalInterceptors.get(2).getClass().toString());

		// Make sure we have the right invokers in the right order
		List<Object> invokers = svc.getInterceptorsWithInvokersForPointcut(Pointcut.TEST_RB);
		assertSame(interceptor0, invokers.get(0));
		assertSame(myInterceptorManual, invokers.get(1));
		assertSame(interceptor1, invokers.get(2));

		// Finally, unregister it
		svc.unregisterInterceptor(myInterceptorManual);
		globalInterceptors = svc.getGlobalInterceptorsForUnitTest();
		assertEquals(2, globalInterceptors.size());
		assertTrue(globalInterceptors.get(0) instanceof MyTestInterceptorOne, globalInterceptors.get(0).getClass().toString());
		assertTrue(globalInterceptors.get(1) instanceof MyTestInterceptorTwo, globalInterceptors.get(1).getClass().toString());

		// Unregister the two others
		assertTrue(svc.hasHooks(Pointcut.TEST_RB));
		svc.unregisterInterceptor(interceptor1);
		assertTrue(svc.hasHooks(Pointcut.TEST_RB));
		svc.unregisterInterceptor(interceptor0);
		assertFalse(svc.hasHooks(Pointcut.TEST_RB));
	}

	@Test
	public void testInvokeGlobalInterceptorMethods() {
		InterceptorService svc = new InterceptorService();

		// Registered in opposite order to verify that the order on the annotation is used
		MyTestInterceptorTwo interceptor1 = new MyTestInterceptorTwo();
		MyTestInterceptorOne interceptor0 = new MyTestInterceptorOne();
		svc.registerInterceptor(interceptor1);
		svc.registerInterceptor(interceptor0);

		if (svc.hasHooks(Pointcut.TEST_RB)) {
			boolean outcome = svc.callHooks(Pointcut.TEST_RB, new HookParams("A", "B"));
			assertTrue(outcome);
		}

		assertThat(myInvocations, contains("MyTestInterceptorOne.testRb", "MyTestInterceptorTwo.testRb"));
		assertSame("A", interceptor0.myLastString0);
		assertSame("A", interceptor1.myLastString0);
		assertSame("B", interceptor1.myLastString1);
	}

	@Test
	public void testInvokeAnonymousInterceptorMethods() {
		InterceptorService svc = new InterceptorService();

		MyTestAnonymousInterceptorOne interceptor0 = new MyTestAnonymousInterceptorOne();
		MyTestAnonymousInterceptorTwo interceptor1 = new MyTestAnonymousInterceptorTwo();
		svc.registerAnonymousInterceptor(Pointcut.TEST_RB, interceptor0);
		svc.registerAnonymousInterceptor(Pointcut.TEST_RB, interceptor1);

		if (svc.hasHooks(Pointcut.TEST_RB)) {
			boolean outcome = svc.callHooks(Pointcut.TEST_RB, new HookParams("A", "B"));
			assertTrue(outcome);
		}

		assertThat(myInvocations, contains("MyTestAnonymousInterceptorOne.testRb", "MyTestAnonymousInterceptorTwo.testRb"));
		assertSame("A", interceptor0.myLastString0);
		assertSame("A", interceptor1.myLastString0);
		assertSame("B", interceptor1.myLastString1);
	}

	@Test
	public void testInvokeUsingSupplierArg() {
		InterceptorService svc = new InterceptorService();

		MyTestInterceptorOne interceptor0 = new MyTestInterceptorOne();
		MyTestInterceptorTwo interceptor1 = new MyTestInterceptorTwo();
		svc.registerInterceptor(interceptor0);
		svc.registerInterceptor(interceptor1);

		boolean outcome = svc.callHooks(Pointcut.TEST_RB, new HookParams("A", "B"));
		assertTrue(outcome);

		assertThat(myInvocations, contains("MyTestInterceptorOne.testRb", "MyTestInterceptorTwo.testRb"));
		assertSame("A", interceptor0.myLastString0);
		assertSame("A", interceptor1.myLastString0);
		assertSame("B", interceptor1.myLastString1);
	}

	@Test
	public void testInvokeGlobalInterceptorMethods_MethodAbortsProcessing() {
		InterceptorService svc = new InterceptorService();

		MyTestInterceptorOne interceptor0 = new MyTestInterceptorOne();
		MyTestInterceptorTwo interceptor1 = new MyTestInterceptorTwo();
		svc.registerInterceptor(interceptor0);
		svc.registerInterceptor(interceptor1);

		interceptor0.myNextReturn = false;

		boolean outcome = svc.callHooks(Pointcut.TEST_RB, new HookParams("A", "B"));
		assertFalse(outcome);

		assertThat(myInvocations, contains("MyTestInterceptorOne.testRb"));
		assertSame("A", interceptor0.myLastString0);
		assertSame(null, interceptor1.myLastString0);
		assertSame(null, interceptor1.myLastString1);
	}

	@Test
	public void testCallHooksInvokedWithNullParameters() {
		InterceptorService svc = new InterceptorService();

		class NullParameterInterceptor {
			private String myValue0 = "";
			private String myValue1 = "";

			@Hook(Pointcut.TEST_RB)
			public void hook(String theValue0, String theValue1) {
				myValue0 = theValue0;
				myValue1 = theValue1;
			}
		}

		NullParameterInterceptor interceptor;
		HookParams params;

		// Both null
		interceptor = new NullParameterInterceptor();
		svc.registerInterceptor(interceptor);
		params = new HookParams()
			.add(String.class, null)
			.add(String.class, null);
		svc.callHooks(Pointcut.TEST_RB, params);
		assertNull(interceptor.myValue0);
		assertNull(interceptor.myValue1);
		svc.unregisterAllInterceptors();

		// First null
		interceptor = new NullParameterInterceptor();
		svc.registerInterceptor(interceptor);
		params = new HookParams()
			.add(String.class, null)
			.add(String.class, "A");
		svc.callHooks(Pointcut.TEST_RB, params);
		assertNull(interceptor.myValue0);
		assertEquals("A", interceptor.myValue1);
		svc.unregisterAllInterceptors();

		// Second null
		interceptor = new NullParameterInterceptor();
		svc.registerInterceptor(interceptor);
		params = new HookParams()
			.add(String.class, "A")
			.add(String.class, null);
		svc.callHooks(Pointcut.TEST_RB, params);
		assertEquals("A", interceptor.myValue0);
		assertNull(interceptor.myValue1);
		svc.unregisterAllInterceptors();

	}

	@Test
	public void testCallHooksLogAndSwallowException() {
		InterceptorService svc = new InterceptorService();

		class LogAndSwallowInterceptor0 {
			private boolean myHit;

			@Hook(Pointcut.TEST_RB)
			public void hook(String theValue0, String theValue1) {
				myHit = true;
				throw new IllegalStateException();
			}
		}
		LogAndSwallowInterceptor0 interceptor0 = new LogAndSwallowInterceptor0();
		svc.registerInterceptor(interceptor0);

		class LogAndSwallowInterceptor1 {
			private boolean myHit;

			@Hook(Pointcut.TEST_RB)
			public void hook(String theValue0, String theValue1) {
				myHit = true;
				throw new IllegalStateException();
			}
		}
		LogAndSwallowInterceptor1 interceptor1 = new LogAndSwallowInterceptor1();
		svc.registerInterceptor(interceptor1);

		class LogAndSwallowInterceptor2 {
			private boolean myHit;

			@Hook(Pointcut.TEST_RB)
			public void hook(String theValue0, String theValue1) {
				myHit = true;
				throw new NullPointerException("AAA");
			}
		}
		LogAndSwallowInterceptor2 interceptor2 = new LogAndSwallowInterceptor2();
		svc.registerInterceptor(interceptor2);

		HookParams params = new HookParams()
			.add(String.class, null)
			.add(String.class, null);

		try {
			svc.callHooks(Pointcut.TEST_RB, params);
			fail();
		} catch (NullPointerException e) {
			assertEquals("AAA", e.getMessage());
		}

		assertTrue(interceptor0.myHit);
		assertTrue(interceptor1.myHit);
		assertTrue(interceptor2.myHit);
	}


	@Test
	public void testCallHooksInvokedWithWrongParameters() {
		InterceptorService svc = new InterceptorService();

		Integer msg = 123;
		CanonicalSubscription subs = new CanonicalSubscription();
		HookParams params = new HookParams(msg, subs);
		try {
			svc.callHooks(Pointcut.TEST_RB, params);
			fail();
		} catch (IllegalArgumentException e) {
			assertThat(e.getMessage(), containsString("Invalid params for pointcut " + Pointcut.TEST_RB + " - Wanted java.lang.String,java.lang.String but found "));
		}
	}

	@Test
	public void testValidateParamTypes() {
		InterceptorService svc = new InterceptorService();

		HookParams params = new HookParams();
		params.add(String.class, "A");
		params.add(String.class, "B");
		boolean validated = svc.haveAppropriateParams(Pointcut.TEST_RB, params);
		assertTrue(validated);
	}

	@Test
	public void testValidateParamTypesMissingParam() {
		InterceptorService svc = new InterceptorService();

		HookParams params = new HookParams();
		params.add(String.class, "A");
		try {
			svc.haveAppropriateParams(Pointcut.TEST_RB, params);
			fail();
		} catch (IllegalArgumentException e) {
			assertEquals(Msg.code(1909) + "Wrong number of params for pointcut " + Pointcut.TEST_RB + " - Wanted java.lang.String,java.lang.String but found [String]", e.getMessage());
		}
	}

	@Test
	public void testValidateParamTypesExtraParam() {
		InterceptorService svc = new InterceptorService();

		HookParams params = new HookParams();
		params.add(String.class, "A");
		params.add(String.class, "B");
		params.add(String.class, "C");
		params.add(String.class, "D");
		params.add(String.class, "E");
		params.add(String.class, "F");
		params.add(String.class, "G");
		try {
			svc.haveAppropriateParams(Pointcut.STORAGE_PRECOMMIT_RESOURCE_UPDATED, params);
			fail();
		} catch (IllegalArgumentException e) {
			assertEquals(Msg.code(1909) + "Wrong number of params for pointcut STORAGE_PRECOMMIT_RESOURCE_UPDATED - Wanted ca.uhn.fhir.rest.api.InterceptorInvocationTimingEnum,ca.uhn.fhir.rest.api.server.RequestDetails,ca.uhn.fhir.rest.api.server.storage.TransactionDetails,ca.uhn.fhir.rest.server.servlet.ServletRequestDetails,org.hl7.fhir.instance.model.api.IBaseResource,org.hl7.fhir.instance.model.api.IBaseResource but found [String, String, String, String, String, String, String]", e.getMessage());
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	@Test
	public void testValidateParamTypesWrongParam() {
		InterceptorService svc = new InterceptorService();

		HookParams params = new HookParams();
		params.add((Class) String.class, 1);
		params.add((Class) String.class, 2);
		params.add((Class) String.class, 3);
		params.add((Class) String.class, 4);
		params.add((Class) String.class, 5);
		params.add((Class) String.class, 6);
		try {
			svc.haveAppropriateParams(Pointcut.STORAGE_PRECOMMIT_RESOURCE_UPDATED, params);
			fail();
		} catch (IllegalArgumentException e) {
			assertEquals("Invalid params for pointcut " + Pointcut.STORAGE_PRECOMMIT_RESOURCE_UPDATED + " - class java.lang.Integer is not of type class java.lang.String", e.getMessage());
		}
	}

	@BeforeEach
	public void before() {
		myInvocations.clear();
	}

	interface TestInterceptorWithAnnotationDefinedOnInterface_Interface {

		@Hook(Pointcut.INTERCEPTOR_REGISTERED)
		void registered();

	}

	@Interceptor(order = 100)
	public class MyTestInterceptorOne {

		private String myLastString0;
		private boolean myNextReturn = true;

		public MyTestInterceptorOne() {
			super();
		}

		@Hook(Pointcut.TEST_RB)
		public boolean testRb(String theString0) {
			myLastString0 = theString0;
			myInvocations.add("MyTestInterceptorOne.testRb");
			return myNextReturn;
		}

	}

	@Interceptor(order = 300)
	public class MyTestInterceptorTwo {
		private String myLastString0;
		private String myLastString1;

		@Hook(Pointcut.TEST_RB)
		public boolean testRb(String theString0, String theString1) {
			myLastString0 = theString0;
			myLastString1 = theString1;
			myInvocations.add("MyTestInterceptorTwo.testRb");
			return true;
		}
	}

	public class MyTestAnonymousInterceptorOne implements IAnonymousInterceptor {
		private String myLastString0;
		@Override
		public void invoke(IPointcut thePointcut, HookParams theArgs) {
			myLastString0 = theArgs.get(String.class, 0);
			myInvocations.add("MyTestAnonymousInterceptorOne.testRb");
		}
	}

	public class MyTestAnonymousInterceptorTwo implements IAnonymousInterceptor {
		private String myLastString0;
		private String myLastString1;

		@Override
		public void invoke(IPointcut thePointcut, HookParams theArgs) {
			myLastString0 = theArgs.get(String.class, 0);
			myLastString1 = theArgs.get(String.class, 1);
			myInvocations.add("MyTestAnonymousInterceptorTwo.testRb");
		}
	}

	@Interceptor(order = 200)
	public class MyTestInterceptorManual {
		@Hook(Pointcut.TEST_RB)
		public void testRb() {
			myInvocations.add("MyTestInterceptorManual.testRb");
		}
	}

	public static class TestInterceptorWithAnnotationDefinedOnInterface_Class implements TestInterceptorWithAnnotationDefinedOnInterface_Interface {

		private int myRegisterCount = 0;

		public int getRegisterCount() {
			return myRegisterCount;
		}

		@Override
		public void registered() {
			myRegisterCount++;
		}
	}

	/**
	 * Just a make-believe version of this class for the unit test
	 */
	private static class CanonicalSubscription {
	}

	@Interceptor()
	public static class InterceptorThatFailsOnRegister {

		@Hook(Pointcut.INTERCEPTOR_REGISTERED)
		public void start() throws Exception {
			throw new Exception("InterceptorThatFailsOnRegister FAILED!");
		}

	}


}
