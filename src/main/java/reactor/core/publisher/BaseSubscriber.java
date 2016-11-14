/*
 * Copyright (c) 2011-2016 Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package reactor.core.publisher;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.Exceptions;
import reactor.core.Receiver;
import reactor.core.Trackable;

/**
 * A simple base class for a {@link Subscriber} implementation that lets the user
 * perform a {@link #request(long)} and {@link #cancel()} on it directly.
 * <p>
 * Override the {@link #doOnSubscribe(Subscription)}, {@link #doOnNext(Object)},
 * {@link #doOnComplete()}, {@link #doOnError(Throwable)} and {@link #doOnCancel()} hooks
 * to customize the base behavior. You also have a termination hook,
 * {@link #doFinally(TerminationType)}.
 * <p>
 * Most of the time, exceptions triggered inside hooks are propagated to
 * {@link #onError(Throwable)} (unless there is a fatal exception).
 *
 * @author Simon Baslé
 */
public class BaseSubscriber<T> implements Subscriber<T>, Subscription, Trackable, Receiver {

	protected Subscription subscription;

	@Override
	public Subscription upstream() {
		return subscription;
	}

	@Override
	public boolean isStarted() {
		return subscription != null;
	}

	/**
	 * Hook for further processing of onSubscribe's Subscription.
	 * Defaults to requesting {@code Long.MAX_VALUE}.
	 *
	 * @param subscription the subscription to optionally process
	 */
	protected void doOnSubscribe(Subscription subscription) {
		request(Long.MAX_VALUE);
	}

	/**
	 * Hook for processing of onNext values.
	 *
	 * @param value the emitted value to process
	 */
	protected void doOnNext(T value) {
		// NO-OP
	}

	/**
	 * Hook for completion processing.
	 */
	protected void doOnComplete() {
		// NO-OP
	}

	/**
	 * Hook for error processing. Default is to call
	 * {@link Exceptions#errorCallbackNotImplemented(Throwable)}.
	 *
	 * @param throwable the error to process
	 */
	protected void doOnError(Throwable throwable) {
		throw Exceptions.errorCallbackNotImplemented(throwable);
	}

	/**
	 * Hook executed when the subscription is cancelled by calling this Subscriber's
	 * {@link #cancel()} method.
	 */
	protected void doOnCancel() {
		//NO-OP
	}

	/**
	 * Hook executed after any of the termination events (onError, onComplete,
	 * cancel). The hook is executed in addition to and after {@link #doOnError(Throwable)},
	 * {@link #doOnComplete()} and {@link #doOnCancel()} hooks.
	 *
	 * @param type the type of termination event that triggered the hook
	 */
	protected void doFinally(BaseSubscriber.TerminationType type) {
		//NO-OP
	}

	@Override
	public final void onSubscribe(Subscription s) {
		if (Operators.validate(subscription, s)) {
			try {
				subscription = s;
				doOnSubscribe(s);
			}
			catch (Throwable throwable) {
				onError(Operators.onOperatorError(s, throwable));
			}
		}
	}

	@Override
	public final void onNext(T value) {
		if (value == null) {
			throw Exceptions.argumentIsNullException();
		}
		try {
			doOnNext(value);
		}
		catch (Throwable throwable) {
			onError(Operators.onOperatorError(subscription, throwable, value));
		}
	}

	@Override
	public final void onError(Throwable t) {
		if (t == null) {
			throw Exceptions.argumentIsNullException();
		}
		doOnError(t);
		doFinally(TerminationType.ERROR);
	}

	@Override
	public final void onComplete() {
		try {
			doOnComplete();
			doFinally(TerminationType.COMPLETE);
		}
		catch (Throwable throwable) {
			onError(Operators.onOperatorError(throwable));
		}
	}

	@Override
	public final void request(long n) {
		try {
			Operators.checkRequest(n);
			Subscription s = this.subscription;
			if (s != null) {
				s.request(n);
			}
		}
		catch (Throwable throwable) {
			innerCancel();
			onError(Operators.onOperatorError(throwable));
		}
	}

	@Override
	public final void cancel() {
		try {
			innerCancel();
			doOnCancel();
			doFinally(TerminationType.CANCEL);
		}
		catch (Throwable throwable) {
			onError(Operators.onOperatorError(subscription, throwable));
		}
	}

	protected void innerCancel() {
		Subscription s = this.subscription;
		if (s != null) {
			this.subscription = null;
			s.cancel();
		}
	}

	@Override
	public boolean isTerminated() {
		return null != subscription && subscription instanceof Trackable && ((Trackable) subscription).isTerminated();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}

	/**
	 * An enum of the various terminations that can trigger a
	 * {@link BaseSubscriber#doFinally(TerminationType)}.
	 */
	public enum TerminationType { COMPLETE, ERROR, CANCEL }
}
