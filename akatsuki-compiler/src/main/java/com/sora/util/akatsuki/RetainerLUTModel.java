package com.sora.util.akatsuki;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleElementVisitor8;

import com.sora.util.akatsuki.AkatsukiConfig.OptFlags;
import com.sora.util.akatsuki.models.ClassInfo;
import com.sora.util.akatsuki.models.SourceCollectingModel;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeSpec.Builder;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.WildcardTypeName;

public class RetainerLUTModel extends SourceCollectingModel<RetainedStateModel> {

	private final Collection<? extends Element> rootElements;

	protected RetainerLUTModel(ProcessorContext context, List<RetainedStateModel> models,
			Collection<? extends Element> rootElements) {
		super(context, models);
		this.rootElements = rootElements;
	}

	public TypeSpec createModel() {
		final Builder typeBuilder = TypeSpec.classBuilder(Akatsuki.RETAINER_CACHE_NAME)
				.addModifiers(Modifier.PUBLIC).addSuperinterface(RetainerCache.class);

		// Class
		final ClassName classType = ClassName.get(Class.class);

		final ClassName bundlerClassName = ClassName.get(BundleRetainer.class);
		Function<TypeName, ParameterizedTypeName> valueNameFunction = parameter -> ParameterizedTypeName
				.get(classType, WildcardTypeName.subtypeOf(parameter == null ? bundlerClassName
						: ParameterizedTypeName.get(bundlerClassName, parameter)));

		Supplier<TypeName> keyValueFunction = () -> ClassName.get(String.class);

		// Map<Class<?>, Class<? extends BundleRetainer<Object>>>
		final ParameterizedTypeName mapType = ParameterizedTypeName.get(ClassName.get(Map.class),
				keyValueFunction.get(), valueNameFunction.apply(null));

		typeBuilder.addField(FieldSpec
				.builder(mapType, "CACHE", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
				.initializer("new $T<>()", HashMap.class).build());

		final CodeBlock.Builder builder = CodeBlock.builder();

		Consumer<Map<String, RetainedStateModel>> modelToMapConsumer = m -> {
			for (Entry<String, RetainedStateModel> entry : m.entrySet()) {
				builder.add("CACHE.put($S, $L);\n", entry.getKey(),
						entry.getValue().classInfo().fullyQualifiedClassName() + ".class");
			}
		};

		Map<String, RetainedStateModel> modelMap = mappingModels().stream().collect(
				Collectors.toMap(m -> m.classModel().fullyQualifiedName(), Function.identity()));

		modelToMapConsumer.accept(modelMap);

		if (context.config().optFlags().contains(OptFlags.VECTORIZE_INHERITANCE)) {
			// find all implementing class (for inheritance)
			for (Element element : rootElements) {
				modelToMapConsumer.accept(findAllTypes(element, modelMap));
			}
		}
		typeBuilder.addStaticBlock(builder.build());

		// <T>
		final TypeVariableName t = TypeVariableName.get("T");

		// Class<? extends BundleRetainer<T>>>
		final ParameterizedTypeName returnType = valueNameFunction.apply(t);
		MethodSpec methodSpec = MethodSpec.methodBuilder("getCached").returns(returnType)
				.addParameter(keyValueFunction.get(), "clazz").addModifiers(Modifier.PUBLIC)
				.addCode("return ($T) CACHE.get(clazz);\n", returnType).addTypeVariable(t)
				.addAnnotation(AnnotationSpec.builder(SuppressWarnings.class)
						.addMember("value", "$S", "unchecked").build())
				.build();
		typeBuilder.addMethod(methodSpec);
		return typeBuilder.build();
	}

	@Override
	public ClassInfo classInfo() {
		return new ClassInfo(Akatsuki.RETAINER_CACHE_PACKAGE, Akatsuki.RETAINER_CACHE_NAME);
	}

	@Override
	public void writeToFile(Filer filer) throws IOException {
		JavaFile.builder(Akatsuki.RETAINER_CACHE_PACKAGE, createModel()).build().writeTo(filer);
	}

	private Map<String, RetainedStateModel> findAllTypes(final Element element,
			final Map<String, RetainedStateModel> referenceMap) {
		Map<String, RetainedStateModel> modelMap = new HashMap<>();
		element.accept(new SimpleElementVisitor8<Void, Map<String, RetainedStateModel>>() {

			@Override
			public Void visitType(TypeElement e, Map<String, RetainedStateModel> map) {
				if (e.getKind() == ElementKind.CLASS) {
					// only process class that isn't in the map
					if (!referenceMap.containsKey(e.getQualifiedName().toString())) {
						findInheritedModel(e, referenceMap.values())
								.ifPresent(m -> map.put(e.getQualifiedName().toString(), m));
					}
					e.getEnclosedElements().forEach(ee -> ee.accept(this, map));
				}
				return null;
			}
		}, modelMap);
		return modelMap;
	}

	private Optional<RetainedStateModel> findInheritedModel(Element element,
			Collection<RetainedStateModel> models) {
		if (element == null || element.getKind() != ElementKind.CLASS
				|| !(element instanceof TypeElement))
			return Optional.empty();
		TypeElement type = (TypeElement) element;
		TypeMirror superMirror = type.getSuperclass();
		return models.stream()
				.filter(m -> context.utils().isSameType(m.classModel().mirror(), superMirror, true))
				.findFirst().map(Optional::of)
				.orElse(findInheritedModel(context.types().asElement(superMirror), models));
	}

}
