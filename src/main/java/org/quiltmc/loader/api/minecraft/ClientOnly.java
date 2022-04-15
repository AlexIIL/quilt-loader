package org.quiltmc.loader.api.minecraft;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Applied to declare that the annotated element is present only in the client environment.
 * <p>
 * When applied to mod code this will result in quilt-loader removing that element when running on the dedicated server.
 * <p>
 * When the annotated element is removed, bytecode associated with the element will not be removed. For example, if a
 * field is removed, its initializer code will not, and will cause an error on execution.
 * </p>
 * <p>
 * If an overriding method has this annotation and its overridden method doesn't, unexpected behavior may happen. If an
 * overridden method has this annotation while the overriding method doesn't, it is safe, but the method can be used
 * from the overridden class only in the specified environment.
 * </p>
 *
 * @see DedicatedServerOnly
 * @see ClientOnlyInterface */
@Retention(RetentionPolicy.CLASS)
@Target({ ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.PACKAGE })
@Documented
public @interface ClientOnly {

	/** @return True if lambda methods referenced by this method should also be stripped. Has no effect when used to
	 *         annotate classes or fields. */
	boolean stripLambdas() default true;
}
