// Copyright 2020 Goldman Sachs
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.finos.legend.engine.language.pure.compiler.toPureGraph;

import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.block.function.Function;
import org.eclipse.collections.api.block.predicate.Predicate;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.multimap.list.ListMultimap;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.block.factory.Functions;
import org.eclipse.collections.impl.block.factory.Predicates;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.tuple.Tuples;
import org.eclipse.collections.impl.utility.ArrayIterate;
import org.eclipse.collections.impl.utility.LazyIterate;
import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.engine.protocol.pure.v1.model.context.EngineErrorType;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Association;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.StereotypePtr;
import org.finos.legend.engine.shared.core.operational.errorManagement.EngineException;
import org.finos.legend.pure.generated.*;
import org.finos.legend.pure.m3.compiler.PropertyOwnerStrategy;
import org.finos.legend.pure.m3.compiler.postprocessing.processor.milestoning.*;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.PropertyOwner;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel._import.ImportStub;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.extension.ElementWithStereotypes;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.extension.Stereotype;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.LambdaFunction;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.AbstractProperty;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.Property;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.QualifiedProperty;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.multiplicity.Multiplicity;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Class;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.FunctionType;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.generics.GenericType;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.valuespecification.*;
import org.finos.legend.pure.m3.navigation.ProcessorSupport;
import org.finos.legend.pure.m3.navigation.profile.Profile;
import org.finos.legend.pure.m3.tools.ListHelper;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.engine.protocol.pure.v1.model.SourceInformation;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

public class Milestoning
{
    private static final String GENERATED_MILESTONING_PATH_SUFFIX = "meta::pure::profiles::" + "milestoning" + "@" + "generatedmilestoningdateproperty";
    private static final String RANGE_PROPERTY_NAME_SUFFIX = "AllVersionsInRange";
    public static CoreInstance businessDate;
    public static CoreInstance processingDate;

    public void MilestoningDates(MilestoningStereotype stereotype, ListIterable<? extends CoreInstance> temporalParameterValues)
    {
        if (stereotype == MilestoningStereotypeEnum.businesstemporal)
        {
            businessDate = temporalParameterValues.get(0);
        }
        else if (stereotype == MilestoningStereotypeEnum.processingtemporal)
        {
            processingDate = temporalParameterValues.get(0);
        }
        else if (stereotype == MilestoningStereotypeEnum.bitemporal)
        {
            processingDate = temporalParameterValues.get(0);
            businessDate = temporalParameterValues.get(1);
        }
    }

    private enum GeneratedMilestoningStereotype
    {
        generatedmilestoningproperty,
        generatedmilestoningdateproperty
    }

    private static class MilestoningPropertyTransformation
    {
        private org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.Property<?, ?> originalProperty;
        private org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.Property<?, ?> edgePointProperty;
        private MutableList<org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.QualifiedProperty<?>> qualfiedPropertiesToAdd;
        private boolean isMilestonedProperty;

        MilestoningPropertyTransformation(org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.Property<?, ?> originalProperty)
        {
            this.originalProperty = originalProperty;
        }

        org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.Property<?, ?> getOriginalProperty()
        {
            return this.originalProperty;
        }

        org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.Property<?, ?> getEdgePointProperty()
        {
            return this.edgePointProperty;
        }

        void setEdgePointProperty(org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.Property<?, ?> property)
        {
            this.edgePointProperty = property;
            this.isMilestonedProperty = true;
        }

        void setQualifiedProperties(MutableList<org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.QualifiedProperty<?>> qualfiedProperties)
        {
            this.qualfiedPropertiesToAdd = qualfiedProperties;
        }

        boolean isTransformed()
        {
            return this.isMilestonedProperty;
        }
    }

    public static Iterable<? extends Property> generateMilestoningProperties(Class<Object> owner, CompileContext context)
    {
        MutableList<Property> generatedMilestoningProperties = Lists.mutable.empty();
        MutableList<MilestoningStereotype> milestoningStereotype = Lists.mutable.ofAll(temporalStereotypes(owner._stereotypes()));
        if (milestoningStereotype.notEmpty())
        {
            MutableList<Property> generatedMilestoningDateProperties = generateMilestoningDateProperties(context, milestoningStereotype.getFirst(), owner);
            Property generatedMilestoningRangeProperty = generateMilestoningRangeProperty(context, milestoningStereotype.getFirst(), owner);
            generatedMilestoningProperties.withAll(generatedMilestoningDateProperties).with(generatedMilestoningRangeProperty);
        }
        return generatedMilestoningProperties;
    }

    private static MutableList<Property> generateMilestoningDateProperties(CompileContext context, MilestoningStereotype milestoningStereotype, Class<Object> owner)
    {
        GenericType dateGenericType = context.pureModel.getGenericType("Date");
        MutableList<Property> generatedMilestoningDateProperties = Lists.mutable.ofAll(milestoningStereotype.getTemporalDatePropertyNames()).collect(name -> new Root_meta_pure_metamodel_function_property_Property_Impl<>(name)
                ._name(name)
                ._genericType(dateGenericType)
                ._classifierGenericType(new Root_meta_pure_metamodel_type_generics_GenericType_Impl("")
                        ._rawType(context.pureModel.getType("meta::pure::metamodel::function::property::Property"))
                        ._typeArguments(Lists.fixedSize.of(owner._classifierGenericType(), dateGenericType)))
                ._multiplicity(context.pureModel.getMultiplicity("one"))
                ._stereotypes(Lists.fixedSize.of(generatedMilestoningStereotype(context, GeneratedMilestoningStereotype.generatedmilestoningdateproperty)))
                ._owner(owner));
        return generatedMilestoningDateProperties;
    }

    private static Property generateMilestoningRangeProperty(CompileContext context, MilestoningStereotype milestoningStereotype, Class<Object> owner)
    {
        GenericType milestoningRangePropertyGenericType = context.resolveGenericType(milestoningStereotype.getMilestoningPropertyClassName());
        Property generatedMilestoningRangeProperty = new Root_meta_pure_metamodel_function_property_Property_Impl<>(MilestoningFunctions.MILESTONING)
                ._name(MilestoningFunctions.MILESTONING)
                ._genericType(milestoningRangePropertyGenericType)
                ._classifierGenericType(new Root_meta_pure_metamodel_type_generics_GenericType_Impl("")
                        ._rawType(context.pureModel.getType("meta::pure::metamodel::function::property::Property"))
                        ._typeArguments(Lists.fixedSize.of(owner._classifierGenericType(), milestoningRangePropertyGenericType)))
                ._multiplicity(context.pureModel.getMultiplicity("zeroone"))
                ._stereotypes(Lists.fixedSize.of(generatedMilestoningStereotype(context, GeneratedMilestoningStereotype.generatedmilestoningdateproperty)))
                ._owner(owner);
        return generatedMilestoningRangeProperty;
    }

    public static void applyMilestoningClassTransformations(CompileContext context, org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Class<?> clazz)
    {
        applyMilestoningPropertyTransformations(context, clazz, org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.ClassAccessor::_properties, org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.ClassAccessor::_qualifiedProperties, org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.ClassAccessor::_originalMilestonedProperties, org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Class::_properties, org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Class::_qualifiedProperties, org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Class::_originalMilestonedPropertiesAddAll);
        applyMilestoningPropertyTransformations(context, clazz, org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.ClassAccessor::_propertiesFromAssociations, org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.ClassAccessor::_qualifiedPropertiesFromAssociations, org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.ClassAccessor::_originalMilestonedProperties, org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Class::_propertiesFromAssociations, org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Class::_qualifiedPropertiesFromAssociations, org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Class::_originalMilestonedPropertiesAddAll);
    }

    public static void applyMilestoningPropertyTransformations(org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.relationship.Association association, org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Class source, org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Class target, MutableList<? extends org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.Property<? extends Object, ? extends Object>> properties, MutableList<? extends org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.QualifiedProperty<? extends Object>> qualifiedProperties, MutableList<? extends org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.Property<? extends Object, ? extends Object>> originalMilestonedProperties)
    {
        properties.removeAllIterable(properties.select(x -> x._genericType()._rawType().equals(source)));
        properties.addAllIterable(target._propertiesFromAssociations().select(x -> ((AbstractProperty<?>) x)._owner().equals(association)));
        qualifiedProperties.removeAllIterable(qualifiedProperties.select(x -> x._genericType()._rawType().equals(source)));
        qualifiedProperties.addAllIterable(target._qualifiedPropertiesFromAssociations().select(x -> ((AbstractProperty<?>) x)._owner().equals(association)));
        originalMilestonedProperties.removeAllIterable(originalMilestonedProperties.select(x -> x._genericType()._rawType().equals(source)));
        originalMilestonedProperties.addAllIterable(target._originalMilestonedProperties().select(x -> ((AbstractProperty<?>) x)._owner().equals(association)));
    }

    private static void applyMilestoningPropertyTransformations(CompileContext context, org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Class<?> clazz, Function<org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Class, RichIterable<? extends org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.Property>> propertiesGetter, Function<org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Class, RichIterable<? extends org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.QualifiedProperty>> qualifiedPropertiesGetter,org.eclipse.collections.api.block.function.Function<Class, RichIterable<? extends Property>> originalMilestonedPropertiesGetter, BiConsumer<org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Class, ListIterable<org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.Property>> propertiesSetter, BiConsumer<org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Class, ListIterable<org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.QualifiedProperty>> qualifiedPropertiesSetter, BiConsumer<org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Class, ListIterable<org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.Property>> originalPropertySetter)
    {
        RichIterable<? extends org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.Property> properties = propertiesGetter.valueOf(clazz).reject(isGeneratedMilestoningNonDateProperty());
        RichIterable<MilestoningPropertyTransformation> milestoningPropertyTransformations = properties.collect(p -> milestoningPropertyTransformations(p, context, clazz, p._owner())).select(MilestoningPropertyTransformation::isTransformed);

        RichIterable<org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.Property<?, ?>> originalProperties = milestoningPropertyTransformations.collect(MilestoningPropertyTransformation::getOriginalProperty);
        RichIterable<org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.Property<?, ?>> edgePointProperties = milestoningPropertyTransformations.collect(MilestoningPropertyTransformation::getEdgePointProperty);
        RichIterable<? extends org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.Property> originalMilestonedProperties = originalMilestonedPropertiesGetter.valueOf(clazz);
        if (!edgePointProperties.isEmpty())
        {
            MutableList<org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.Property> propertiesUpdated = Lists.mutable.withAll(properties.select(p -> !originalProperties.contains(p))).withAll((Iterable) edgePointProperties);
            MutableList<org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.QualifiedProperty> qualifiedPropertiesUpdated = Lists.mutable.withAll(qualifiedPropertiesGetter.valueOf(clazz)).withAll((Iterable) milestoningPropertyTransformations.flatCollect(t -> t.qualfiedPropertiesToAdd));
            MutableList<org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.Property> originalMilestonedPropertiesUpdated = Lists.mutable.withAll(originalProperties.reject(p -> originalMilestonedProperties.anySatisfy(o -> o.getName().equals(p.getName()))));
            propertiesSetter.accept(clazz, propertiesUpdated);
            qualifiedPropertiesSetter.accept(clazz, qualifiedPropertiesUpdated);
            originalPropertySetter.accept(clazz, originalMilestonedPropertiesUpdated);
        }
    }

    private static MilestoningPropertyTransformation milestoningPropertyTransformations(Property<?, ?> originalProperty, CompileContext context, Class<?> sourceClass, PropertyOwner propertyOwner)
    {
        List<MilestoningStereotype> returnTypeMilestoningStereotypes = temporalStereotypes(originalProperty._genericType()._rawType()._stereotypes());
        MilestoningPropertyTransformation milestoningPropertyTransformation = new MilestoningPropertyTransformation(originalProperty);

        if (!returnTypeMilestoningStereotypes.isEmpty())
        {
            MilestoningStereotype returnTypeMilestoningStereotype = returnTypeMilestoningStereotypes.get(0);
            MutableList<Stereotype> stereotypes = Lists.mutable.withAll(originalProperty._stereotypes());
            MutableList<Stereotype> withMilestoningStereotype = stereotypes.with(generatedMilestoningStereotype(context, GeneratedMilestoningStereotype.generatedmilestoningproperty));

            Property<?, ?> edgePointProperty = edgePointProperty(propertyOwner, originalProperty, stereotypes);
            milestoningPropertyTransformation.setEdgePointProperty(edgePointProperty);

            MutableList<QualifiedProperty<?>> milestoningQualifiedPropertyWithArg = newSingleDateMilestoningQualifiedPropertyWithArg(context, sourceClass, propertyOwner, originalProperty, returnTypeMilestoningStereotype, withMilestoningStereotype, edgePointProperty);
            MutableList<QualifiedProperty<?>> milestoningRangeQualifiedProperty = generateMilestoningRangeQualifiedProperty(context, sourceClass, propertyOwner, originalProperty, returnTypeMilestoningStereotype, withMilestoningStereotype, edgePointProperty);

            List<MilestoningStereotype> sourceMilestoningStereotype = temporalStereotypes(sourceClass._stereotypes());
            milestoningQualifiedPropertyWithArg.withAll(milestoningRangeQualifiedProperty);
            if(!sourceMilestoningStereotype.isEmpty() && (sourceMilestoningStereotype.get(0).equals(returnTypeMilestoningStereotypes.get(0)) || MilestoningStereotypeEnum.bitemporal.equals(sourceMilestoningStereotype.get(0))))
            {
                QualifiedProperty<?> milestoningQualifiedPropertyNoArg = newSingleDateMilestoningQualifiedPropertyNoArg(context, sourceClass, propertyOwner, originalProperty, returnTypeMilestoningStereotype, withMilestoningStereotype, edgePointProperty);
                milestoningQualifiedPropertyWithArg.add(milestoningQualifiedPropertyNoArg);
            }
            milestoningPropertyTransformation.setQualifiedProperties(milestoningQualifiedPropertyWithArg);
        }
        return milestoningPropertyTransformation;
    }

    private static org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.Property<?, ?> edgePointProperty(PropertyOwner owner, org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.Property<?, ?> originalProperty, MutableList<Stereotype> stereotypes)
    {
        String edgePointPropertyName = MilestoningFunctions.getEdgePointPropertyName(originalProperty._name());
        org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.multiplicity.Multiplicity multiplicity = new Root_meta_pure_metamodel_multiplicity_Multiplicity_Impl("")
                ._lowerBound(originalProperty._multiplicity()._lowerBound())
                ._upperBound(new Root_meta_pure_metamodel_multiplicity_MultiplicityValue_Impl("")._value(-1L));
        return newProperty(owner, originalProperty, edgePointPropertyName, multiplicity, stereotypes);
    }

    private static QualifiedProperty<?> newSingleDateMilestoningQualifiedPropertyNoArg(CompileContext context, Class<?> sourceClass, PropertyOwner propertyOwner, Property originalProperty, MilestoningStereotype returnTypeMilestoningStereotype, MutableList<Stereotype> stereotypes, Property edgePointProperty)
    {
        String qualifiedPropertyName = originalProperty._name();
        org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.QualifiedProperty<?> qualifiedProperty = getQualifiedProperty(propertyOwner, originalProperty, qualifiedPropertyName, originalProperty._multiplicity(), stereotypes);

        VariableExpression thisVar = new Root_meta_pure_metamodel_valuespecification_VariableExpression_Impl("")._name("this")._multiplicity(context.pureModel.getMultiplicity("one"))._genericType(sourceClass._classifierGenericType()._typeArguments().toList().get(0));
        VariableExpression v_milestoning = new Root_meta_pure_metamodel_valuespecification_VariableExpression_Impl("")._name("v_milestoning")._multiplicity(context.pureModel.getMultiplicity("one"))._genericType(edgePointProperty._genericType());

        ListIterable<Pair<VariableExpression, SimpleFunctionExpression>> datesToCompare = returnTypeMilestoningStereotype.getTemporalDatePropertyNames().collect(d ->
        {
            VariableExpression inputTemporalDate = new Root_meta_pure_metamodel_valuespecification_VariableExpression_Impl("")._name(d)._multiplicity(context.pureModel.getMultiplicity("one"))._genericType(context.pureModel.getGenericType("Date"));
            org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.Property<?, ?> temporalDateProperty = ((Class<Object>) originalProperty._genericType()._rawType())._properties().detect(p -> p._name().equals(d));
            SimpleFunctionExpression temporalDatePropertyExp = new Root_meta_pure_metamodel_valuespecification_SimpleFunctionExpression_Impl("")
                    ._func(temporalDateProperty)
                    ._propertyName(new Root_meta_pure_metamodel_valuespecification_InstanceValue_Impl("")._values(Lists.fixedSize.of(d)))
                    ._genericType(context.pureModel.getGenericType("Date"))
                    ._multiplicity(context.pureModel.getMultiplicity("one"))
                    ._parametersValues(Lists.fixedSize.of(v_milestoning));
            return Tuples.pair(inputTemporalDate, temporalDatePropertyExp);
        });

        ListIterable<SimpleFunctionExpression> equalExpressions = datesToCompare.collect(p -> new Root_meta_pure_metamodel_valuespecification_SimpleFunctionExpression_Impl("")
                ._func(context.pureModel.getFunction("meta::pure::functions::boolean::eq_Any_1__Any_1__Boolean_1_", true))
                ._functionName("eq")
                ._genericType(context.pureModel.getGenericType("Boolean"))
                ._multiplicity(context.pureModel.getMultiplicity("one"))
                ._parametersValues(Lists.fixedSize.<ValueSpecification>of(p.getTwo(), p.getOne())));
        SimpleFunctionExpression equalExpression = equalExpressions.size() == 1 ? equalExpressions.get(0) : new Root_meta_pure_metamodel_valuespecification_SimpleFunctionExpression_Impl("")
                ._func(context.pureModel.getFunction("meta::pure::functions::boolean::and_Boolean_1__Boolean_1__Boolean_1_", true))
                ._functionName("and")
                ._genericType(context.pureModel.getGenericType("Boolean"))
                ._multiplicity(context.pureModel.getMultiplicity("one"))
                ._parametersValues(equalExpressions);

        GenericType functionType = PureModel.buildFunctionType(Lists.fixedSize.of(v_milestoning), context.pureModel.getGenericType("Boolean"), context.pureModel.getMultiplicity("one"));

        LambdaFunction filterLambda = new Root_meta_pure_metamodel_function_LambdaFunction_Impl("")
                ._classifierGenericType(new Root_meta_pure_metamodel_type_generics_GenericType_Impl("")
                        ._rawType(context.pureModel.getType("meta::pure::metamodel::function::LambdaFunction"))
                        ._typeArguments(FastList.newListWith(functionType)))
                ._openVariables(Lists.fixedSize.of("this"))
                ._expressionSequence(Lists.fixedSize.of(equalExpression));

        InstanceValue filterInstanceValue = new Root_meta_pure_metamodel_valuespecification_InstanceValue_Impl("")
                ._genericType(filterLambda._classifierGenericType())
                ._multiplicity(context.pureModel.getMultiplicity("one"))
                ._values(Lists.fixedSize.of(filterLambda));

        SimpleFunctionExpression filterLhs = new Root_meta_pure_metamodel_valuespecification_SimpleFunctionExpression_Impl("")
                ._func(edgePointProperty)
                ._propertyName(new Root_meta_pure_metamodel_valuespecification_InstanceValue_Impl("")._values(Lists.fixedSize.of(edgePointProperty._name())))
                ._genericType(edgePointProperty._genericType())
                ._multiplicity(edgePointProperty._multiplicity())
                ._parametersValues(Lists.fixedSize.of(thisVar));

        SimpleFunctionExpression filterExpression = new Root_meta_pure_metamodel_valuespecification_SimpleFunctionExpression_Impl("")
                ._func(context.pureModel.getFunction("meta::pure::functions::collection::filter_T_MANY__Function_1__T_MANY_", true))
                ._functionName("filter")
                ._genericType(qualifiedProperty._genericType())
                ._multiplicity(context.pureModel.getMultiplicity("zeromany"))
                ._parametersValues(Lists.fixedSize.of(filterLhs, filterInstanceValue));

        GenericType classifierGenericType = new Root_meta_pure_metamodel_type_generics_GenericType_Impl("")
                ._rawType(context.pureModel.getType("meta::pure::metamodel::function::property::QualifiedProperty"))
                ._typeArguments(Lists.fixedSize.of(PureModel.buildFunctionType(Lists.mutable.of(thisVar), qualifiedProperty._genericType(), originalProperty._multiplicity())));

        qualifiedProperty._classifierGenericType(classifierGenericType);
        qualifiedProperty._expressionSequence(Lists.fixedSize.of(filterExpression));

        return qualifiedProperty;
    }

    private static MutableList<QualifiedProperty<?>> newSingleDateMilestoningQualifiedPropertyWithArg(CompileContext context, Class<?> sourceClass, PropertyOwner propertyOwner, Property originalProperty, MilestoningStereotype returnTypeMilestoningStereotype, MutableList<Stereotype> stereotypes, Property edgePointProperty)
    {
        String qualifiedPropertyName = originalProperty._name();
        org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.QualifiedProperty<?> qualifiedProperty = getQualifiedProperty(propertyOwner, originalProperty, qualifiedPropertyName, originalProperty._multiplicity(), stereotypes);

        VariableExpression thisVar = new Root_meta_pure_metamodel_valuespecification_VariableExpression_Impl("")._name("this")._multiplicity(context.pureModel.getMultiplicity("one"))._genericType(sourceClass._classifierGenericType()._typeArguments().toList().get(0));
        VariableExpression v_milestoning = new Root_meta_pure_metamodel_valuespecification_VariableExpression_Impl("")._name("v_milestoning")._multiplicity(context.pureModel.getMultiplicity("one"))._genericType(edgePointProperty._genericType());

        ListIterable<Pair<VariableExpression, SimpleFunctionExpression>> datesToCompare = returnTypeMilestoningStereotype.getTemporalDatePropertyNames().collect(d ->
        {
            VariableExpression inputTemporalDate = new Root_meta_pure_metamodel_valuespecification_VariableExpression_Impl("")._name(d)._multiplicity(context.pureModel.getMultiplicity("one"))._genericType(context.pureModel.getGenericType("Date"));
            org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.Property<?, ?> temporalDateProperty = ((Class<Object>) originalProperty._genericType()._rawType())._properties().detect(p -> p._name().equals(d));
            SimpleFunctionExpression temporalDatePropertyExp = new Root_meta_pure_metamodel_valuespecification_SimpleFunctionExpression_Impl("")
                    ._func(temporalDateProperty)
                    ._propertyName(new Root_meta_pure_metamodel_valuespecification_InstanceValue_Impl("")._values(Lists.fixedSize.of(d)))
                    ._genericType(context.pureModel.getGenericType("Date"))
                    ._multiplicity(context.pureModel.getMultiplicity("one"))
                    ._parametersValues(Lists.fixedSize.of(v_milestoning));
            return Tuples.pair(inputTemporalDate, temporalDatePropertyExp);
        });

        ListIterable<SimpleFunctionExpression> equalExpressions = datesToCompare.collect(p -> new Root_meta_pure_metamodel_valuespecification_SimpleFunctionExpression_Impl("")
                ._func(context.pureModel.getFunction("meta::pure::functions::boolean::eq_Any_1__Any_1__Boolean_1_", true))
                ._functionName("eq")
                ._genericType(context.pureModel.getGenericType("Boolean"))
                ._multiplicity(context.pureModel.getMultiplicity("one"))
                ._parametersValues(Lists.fixedSize.<ValueSpecification>of(p.getTwo(), p.getOne())));
        SimpleFunctionExpression equalExpression = equalExpressions.size() == 1 ? equalExpressions.get(0) : new Root_meta_pure_metamodel_valuespecification_SimpleFunctionExpression_Impl("")
                ._func(context.pureModel.getFunction("meta::pure::functions::boolean::and_Boolean_1__Boolean_1__Boolean_1_", true))
                ._functionName("and")
                ._genericType(context.pureModel.getGenericType("Boolean"))
                ._multiplicity(context.pureModel.getMultiplicity("one"))
                ._parametersValues(equalExpressions);

        GenericType functionType = PureModel.buildFunctionType(Lists.fixedSize.of(v_milestoning), context.pureModel.getGenericType("Boolean"), context.pureModel.getMultiplicity("one"));

        LambdaFunction filterLambda = new Root_meta_pure_metamodel_function_LambdaFunction_Impl("")
                ._classifierGenericType(new Root_meta_pure_metamodel_type_generics_GenericType_Impl("")
                        ._rawType(context.pureModel.getType("meta::pure::metamodel::function::LambdaFunction"))
                        ._typeArguments(FastList.newListWith(functionType)))
                ._openVariables(returnTypeMilestoningStereotype.getTemporalDatePropertyNames())
                ._expressionSequence(Lists.fixedSize.of(equalExpression));

        InstanceValue filterInstanceValue = new Root_meta_pure_metamodel_valuespecification_InstanceValue_Impl("")
                ._genericType(filterLambda._classifierGenericType())
                ._multiplicity(context.pureModel.getMultiplicity("one"))
                ._values(Lists.fixedSize.of(filterLambda));

        SimpleFunctionExpression filterLhs = new Root_meta_pure_metamodel_valuespecification_SimpleFunctionExpression_Impl("")
                ._func(edgePointProperty)
                ._propertyName(new Root_meta_pure_metamodel_valuespecification_InstanceValue_Impl("")._values(Lists.fixedSize.of(edgePointProperty._name())))
                ._genericType(edgePointProperty._genericType())
                ._multiplicity(edgePointProperty._multiplicity())
                ._parametersValues(Lists.fixedSize.of(thisVar));

        SimpleFunctionExpression filterExpression = new Root_meta_pure_metamodel_valuespecification_SimpleFunctionExpression_Impl("")
                ._func(context.pureModel.getFunction("meta::pure::functions::collection::filter_T_MANY__Function_1__T_MANY_", true))
                ._functionName("filter")
                ._genericType(qualifiedProperty._genericType())
                ._multiplicity(context.pureModel.getMultiplicity("zeromany"))
                ._parametersValues(Lists.fixedSize.of(filterLhs, filterInstanceValue));

        GenericType classifierGenericType = new Root_meta_pure_metamodel_type_generics_GenericType_Impl("")
                ._rawType(context.pureModel.getType("meta::pure::metamodel::function::property::QualifiedProperty"))
                ._typeArguments(Lists.fixedSize.of(PureModel.buildFunctionType(Lists.mutable.of(thisVar).withAll(datesToCompare.collect(Functions.firstOfPair())), qualifiedProperty._genericType(), originalProperty._multiplicity())));

        qualifiedProperty._classifierGenericType(classifierGenericType);
        qualifiedProperty._expressionSequence(Lists.fixedSize.of(filterExpression));

        return Lists.mutable.of(qualifiedProperty);
    }

    private static MutableList<QualifiedProperty<?>> generateMilestoningRangeQualifiedProperty(CompileContext context, Class<?> sourceClass, PropertyOwner propertyOwner, Property originalProperty, MilestoningStereotype returnTypeMilestoningStereotype, MutableList<Stereotype> stereotypes, Property edgePointProperty)
    {
        MutableList<QualifiedProperty<?>> generatedMilestoningRangeQualifiedProperty = Lists.mutable.empty();

        if (UNI_TEMPORAL_STEREOTYPE_NAMES.contains(returnTypeMilestoningStereotype.getPurePlatformStereotypeName()))
        {
            String qualifiedPropertyName = MilestoningFunctions.getRangePropertyName(originalProperty._name());
            String temporalDatePropertyName = returnTypeMilestoningStereotype.getTemporalDatePropertyNames().getFirst();

            VariableExpression thisVar = new Root_meta_pure_metamodel_valuespecification_VariableExpression_Impl("")
                    ._name("this")
                    ._multiplicity(context.pureModel.getMultiplicity("one"))
                    ._genericType(sourceClass._classifierGenericType()._typeArguments().getFirst());

            VariableExpression v_milestoning = new Root_meta_pure_metamodel_valuespecification_VariableExpression_Impl("")
                    ._name("v_milestoning")
                    ._multiplicity(context.pureModel.getMultiplicity("one"))
                    ._genericType(edgePointProperty._genericType());

            VariableExpression inputStartDate = new Root_meta_pure_metamodel_valuespecification_VariableExpression_Impl("")
                    ._name("start")
                    ._multiplicity(context.pureModel.getMultiplicity("one"))
                    ._genericType(context.pureModel.getGenericType("Date"));

            VariableExpression inputEndDate = new Root_meta_pure_metamodel_valuespecification_VariableExpression_Impl("")
                    ._name("end")
                    ._multiplicity(context.pureModel.getMultiplicity("one"))
                    ._genericType(context.pureModel.getGenericType("Date"));

            SimpleFunctionExpression temporalDatePropertyExp = new Root_meta_pure_metamodel_valuespecification_SimpleFunctionExpression_Impl("")
                    ._func(HelperModelBuilder.getOwnedProperty((Class<Object>) originalProperty._genericType()._rawType(), temporalDatePropertyName, context.pureModel.getExecutionSupport()))
                    ._propertyName(new Root_meta_pure_metamodel_valuespecification_InstanceValue_Impl("")._values(Lists.mutable.of(temporalDatePropertyName)))
                    ._genericType(context.pureModel.getGenericType("Date"))
                    ._multiplicity(context.pureModel.getMultiplicity("one"))
                    ._parametersValues(Lists.mutable.of(v_milestoning));

            SimpleFunctionExpression equalExpression = new Root_meta_pure_metamodel_valuespecification_SimpleFunctionExpression_Impl("")
                    ._func(context.pureModel.getFunction("meta::pure::functions::boolean::eq_Any_1__Any_1__Boolean_1_", true))
                    ._functionName("eq")
                    ._genericType(context.pureModel.getGenericType("Boolean"))
                    ._multiplicity(context.pureModel.getMultiplicity("one"))
                    ._parametersValues(Lists.mutable.of(temporalDatePropertyExp, inputStartDate));

            LambdaFunction filterLambda = new Root_meta_pure_metamodel_function_LambdaFunction_Impl("")
                    ._classifierGenericType(new Root_meta_pure_metamodel_type_generics_GenericType_Impl("")
                            ._rawType(context.pureModel.getType("meta::pure::metamodel::function::LambdaFunction"))
                            ._typeArguments(Lists.mutable.of(PureModel.buildFunctionType(Lists.mutable.of(v_milestoning), context.pureModel.getGenericType("Boolean"), context.pureModel.getMultiplicity("one")))))
                    ._openVariables(Lists.mutable.of(temporalDatePropertyName))
                    ._expressionSequence(Lists.mutable.of(equalExpression));

            InstanceValue filterInstanceValue = new Root_meta_pure_metamodel_valuespecification_InstanceValue_Impl("")
                    ._genericType(filterLambda._classifierGenericType())
                    ._multiplicity(context.pureModel.getMultiplicity("one"))
                    ._values(Lists.mutable.of(filterLambda));

            SimpleFunctionExpression filterLhs = new Root_meta_pure_metamodel_valuespecification_SimpleFunctionExpression_Impl("")
                    ._func(edgePointProperty)
                    ._propertyName(new Root_meta_pure_metamodel_valuespecification_InstanceValue_Impl("")._values(Lists.mutable.of(edgePointProperty._name())))
                    ._genericType(edgePointProperty._genericType())
                    ._multiplicity(edgePointProperty._multiplicity())
                    ._parametersValues(Lists.mutable.of(thisVar));

            SimpleFunctionExpression filterExpression = new Root_meta_pure_metamodel_valuespecification_SimpleFunctionExpression_Impl("")
                    ._func(context.pureModel.getFunction("meta::pure::functions::collection::filter_T_MANY__Function_1__T_MANY_", true))
                    ._functionName("filter")
                    ._genericType(originalProperty._genericType())
                    ._multiplicity(context.pureModel.getMultiplicity("zeromany"))
                    ._parametersValues(Lists.mutable.of(filterLhs, filterInstanceValue));

            QualifiedProperty<?> milestoningRangeQualifiedProperty = getQualifiedProperty(propertyOwner, originalProperty, qualifiedPropertyName, originalProperty._multiplicity(), stereotypes)
                    ._classifierGenericType(new Root_meta_pure_metamodel_type_generics_GenericType_Impl("")
                            ._rawType(context.pureModel.getType("meta::pure::metamodel::function::property::QualifiedProperty"))
                            ._typeArguments(Lists.mutable.of(PureModel.buildFunctionType(Lists.mutable.of(thisVar, inputStartDate, inputEndDate), originalProperty._genericType(), originalProperty._multiplicity()))))
                    ._expressionSequence(Lists.mutable.of(filterExpression));

            generatedMilestoningRangeQualifiedProperty.add(milestoningRangeQualifiedProperty);
        }
        return generatedMilestoningRangeQualifiedProperty;
    }

    private static org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.Property<?, ?> newProperty(PropertyOwner owner, org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.Property<?, ?> originalProperty, String name, org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.multiplicity.Multiplicity multiplicity, ListIterable<Stereotype> stereotypes)
    {
        org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.Property<?, ?> property = newAbstractProperty(owner, originalProperty, name, multiplicity, stereotypes, new Root_meta_pure_metamodel_function_property_Property_Impl(name));
        property._classifierGenericType(originalProperty._classifierGenericType());
        return property;
    }

    private static QualifiedProperty<?> getQualifiedProperty(PropertyOwner owner, org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.Property<?, ?> originalProperty, String name, org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.multiplicity.Multiplicity multiplicity, ListIterable<Stereotype> stereotypes)
    {
        return newAbstractProperty(owner, originalProperty, name, multiplicity, stereotypes, new Root_meta_pure_metamodel_function_property_QualifiedProperty_Impl(name));
    }

    private static <T extends AbstractProperty<?>> T newAbstractProperty(PropertyOwner owner, Property<?, ?> originalProperty, String name, Multiplicity multiplicity, ListIterable<Stereotype> stereotypes, T property)
    {
        property._name(name)
                ._functionName(name)
                ._genericType(originalProperty._genericType())
                ._multiplicity(multiplicity)
                ._stereotypes(stereotypes)
                ._taggedValues(originalProperty._taggedValues())
                ._owner(owner);
        return property;
    }

    private static Predicate<Property<?, ?>> isGeneratedMilestoningDateProperty()
    {
        return p -> p._stereotypes().anySatisfy(s -> s._value().equals(GeneratedMilestoningStereotype.generatedmilestoningdateproperty.name()));
    }

    private static Predicate<Property> isGeneratedMilestoningNonDateProperty()
    {
        return p -> p._stereotypes().anySatisfy(s -> s._value().equals(GeneratedMilestoningStereotype.generatedmilestoningproperty.name()));
    }

    public static List<MilestoningStereotype> temporalStereotypes(RichIterable<? extends Stereotype> stereotypes)
    {
        return ArrayIterate.select(MilestoningStereotypeEnum.values(), e -> stereotypes.anySatisfy(s -> s._value().equals(e.getPurePlatformStereotypeName())));
    }

    private static Stereotype generatedMilestoningStereotype(CompileContext context, GeneratedMilestoningStereotype generatedMilestoningStereotype)
    {
        return context.pureModel.getStereotype("meta::pure::profiles::milestoning", generatedMilestoningStereotype.name());
    }

    private static final ImmutableList<String> UNI_TEMPORAL_STEREOTYPE_NAMES = Lists.immutable.of("businesstemporal", "processingtemporal");

    public static boolean isAllVersionsInRangeProperty(CoreInstance property, CompileContext context)
    {
        return isGeneratedMilestoningProperty(property, context) && property.getValueForMetaPropertyToOne("name").getName().endsWith(RANGE_PROPERTY_NAME_SUFFIX);
    }

    public static boolean isGeneratedMilestoningProperty(CoreInstance property, CompileContext context, final String stereotype, final String milestoningPathSuffix)
    {
        CoreInstance profile = context.pureModel.getProfile("meta::pure::profiles::milestoning");
        final CoreInstance milestoningStereotype = Profile.findStereotype(profile, stereotype);
        ListIterable<? extends CoreInstance> stereotypes = property instanceof ElementWithStereotypes ? (ListIterable<? extends CoreInstance>) ((ElementWithStereotypes) property)._stereotypes() : Lists.immutable.<CoreInstance>empty();
        return stereotypes.detect(new Predicate<CoreInstance>()
        {
            @Override
            public boolean accept(CoreInstance stereotype)
            {
                boolean result;
                if (stereotype == null)
                {
                    result = false;
                }
                else if (stereotype instanceof ImportStub)
                {
                    String idOrPath = ((ImportStub)stereotype)._idOrPath();
                    result = idOrPath.endsWith(milestoningPathSuffix);
                }
                else
                {
                    result = milestoningStereotype.equals(stereotype);
                }
                return result;
            }
        }) != null;
    }

    public static boolean isGeneratedMilestoningProperty(CoreInstance property, CompileContext context)
    {
        return isGeneratedMilestoningProperty(property, context, "generatedmilestoningproperty", GENERATED_MILESTONING_PATH_SUFFIX);
    }

    public static boolean isGeneratedQualifiedProperty(CoreInstance property, CompileContext context)
    {
        return property instanceof QualifiedProperty && isGeneratedMilestoningProperty(property, context) && !isAllVersionsInRangeProperty(property, context);
    }

    private static Function<CoreInstance, String> toStereotypeName()
    {
        return new Function<CoreInstance, String>()
        {
            @Override
            public String valueOf(CoreInstance stereotype)
            {
                if (stereotype instanceof ImportStub)
                {
                    String idOrPath = ((ImportStub)stereotype)._idOrPath();
                    return idOrPath.split("@")[1];
                }
                else
                {
                    return CoreInstance.GET_NAME.valueOf(stereotype);
                }
            }
        };
    }

    private static final Function<MilestoningStereotypeEnum, Pair<String, MilestoningStereotypeEnum>> MILESTONINGSTEREOTYPE_TO_PURE_NAMED_STEREOTYPE_PAIR = new Function<MilestoningStereotypeEnum, Pair<String, MilestoningStereotypeEnum>>()
    {
        @Override
        public Pair<String, MilestoningStereotypeEnum> valueOf(MilestoningStereotypeEnum milestoningStereotype)
        {
            return Tuples.pair(milestoningStereotype.getPurePlatformStereotypeName(), milestoningStereotype);
        }
    };

    public static ListIterable<MilestoningStereotypeEnum> getTemporalStereoTypesExcludingParents(CoreInstance cls)
    {
        ListIterable<? extends CoreInstance> clsStereotypes = cls instanceof ElementWithStereotypes ? ((ElementWithStereotypes)cls)._stereotypesCoreInstance().toList() : Lists.immutable.<CoreInstance>empty();
        ListIterable<String> clsStereotypeNames = clsStereotypes.collect(toStereotypeName());
        MutableList<Pair<String, MilestoningStereotypeEnum>> temporalStereoTypeNames = ArrayIterate.collect(MilestoningStereotypeEnum.values(), MILESTONINGSTEREOTYPE_TO_PURE_NAMED_STEREOTYPE_PAIR);
        return temporalStereoTypeNames.select(Predicates.attributePredicate(Functions.<String>firstOfPair(), Predicates.in(clsStereotypeNames))).collect(Functions.<MilestoningStereotypeEnum>secondOfPair());
    }

    private static int getCountOfParametersSatisfyingMilestoningDateRequirments(QualifiedProperty milestonedQualifiedProperty, CompileContext context)
    {
        if (!isGeneratedMilestoningProperty(milestonedQualifiedProperty, context))
        {
            throw new EngineException("Unable to get milestoning date parameters for non milestoned QualifiedProperty: " + milestonedQualifiedProperty.getName());
        }
        Class returnType = (Class)milestonedQualifiedProperty._genericType()._rawType();
        MilestoningStereotype milestoningStereotype = Lists.mutable.ofAll(temporalStereotypes(returnType._stereotypes())).getFirst();
        return 1 + milestoningStereotype.getTemporalDatePropertyNames().size();
    }

    public static boolean isGeneratedMilestonedQualifiedPropertyWithMissingDates(CoreInstance property, CompileContext context, Integer parametersCount)
    {
        if (isGeneratedQualifiedProperty(property, context))
        {
            return parametersCount != getCountOfParametersSatisfyingMilestoningDateRequirments((QualifiedProperty)property, context);
        }
        return false;
    }

    private static boolean isProcessingTemporal(MilestoningStereotype milestoningStereotype)
    {
        return milestoningStereotype != null && milestoningStereotype.getPurePlatformStereotypeName() == "processingtemporal";
    }

    private static boolean isSingleDateTemporal(MilestoningStereotype milestoningStereotype)
    {
        return milestoningStereotype != null && isProcessingTemporal(milestoningStereotype) || milestoningStereotype.getPurePlatformStereotypeName() == "businesstemporal";
    }

    private static CoreInstance getMilestonedPropertyOwningType(CoreInstance property)
    {
        CoreInstance owner = ((AbstractProperty<?>) property)._owner();
        if (owner instanceof Class)
        {
            return owner;
        }
        else if (owner instanceof org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.relationship.Association)
        {
            return ((org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.relationship.Association) owner)._originalMilestonedProperties().getFirst()._genericType()._rawType();

        }
        return null;
    }

    private static Pair<MilestoningStereotype, MilestoningStereotype> getSourceTargetMilestoningStereotypeEnums(CoreInstance func)
    {
        CoreInstance sourceType = getMilestonedPropertyOwningType(func);
        CoreInstance targetType = func instanceof AbstractProperty ? ((AbstractProperty)func)._genericType()._rawType() : null;
        Class source = (Class) sourceType;
        Class target = (Class) targetType;
        MilestoningStereotype sourceTypeMilestoningStereotype = Milestoning.temporalStereotypes(source._stereotypes()).get(0);
        MilestoningStereotype targetTypeMilestoningStereotype = Milestoning.temporalStereotypes(target._stereotypes()).get(0);
        System.out.println(sourceTypeMilestoningStereotype + " " + targetTypeMilestoningStereotype);
        return Tuples.pair(sourceTypeMilestoningStereotype, targetTypeMilestoningStereotype);
    }

    private static boolean isBiTemporal(MilestoningStereotype milestoningStereotype)
    {
        return milestoningStereotype != null && milestoningStereotype.getPurePlatformStereotypeName() == "bitemporal";
    }

    private static void setMilestoningDateParameters(CoreInstance[] dateParamValues, int index, CoreInstance milestoningDate)
    {
        dateParamValues[index] = milestoningDate;
    }

    private static boolean oneDateParamSupplied(ListIterable<? extends CoreInstance> parameterValues)
    {
        return parameterValues.size() == 2;
    }

    private static void setBiTemporaDates(CoreInstance[] dateParamValues)
    {
        setMilestoningDateParameters(dateParamValues, MilestoningStereotypeEnum.processingtemporal.positionInTemporalParameterValues(),processingDate);
        setMilestoningDateParameters(dateParamValues, MilestoningStereotypeEnum.businesstemporal.positionInTemporalParameterValues(), businessDate);
    }

    private static boolean noDateParamSupplied(ListIterable<? extends CoreInstance> parameterValues)
    {
        return parameterValues.size() == 1;
    }

    public static void applyPropertyFunctionExpressionMilestonedDates(FunctionExpression fe, CoreInstance func, SourceInformation sourceInformation)
    {
        Pair<MilestoningStereotype, MilestoningStereotype> sourceTargetMilestoningStereotypeEnums = getSourceTargetMilestoningStereotypeEnums(func);
        MilestoningStereotype sourceTypeMilestoning = sourceTargetMilestoningStereotypeEnums.getOne();
        MilestoningStereotype targetTypeMilestoning = sourceTargetMilestoningStereotypeEnums.getTwo();
        String propertyName = func.getName();
        MutableList<? extends ValueSpecification> parametersValues = fe._parametersValues().toList();
        ValueSpecification[] milestoningDateParameters = new ValueSpecification[targetTypeMilestoning.getTemporalDatePropertyNames().size()];
        fe._originalMilestonedPropertyParametersValues(fe._parametersValues());

        if (isBiTemporal(targetTypeMilestoning))
        {
            if (isBiTemporal(sourceTypeMilestoning) && oneDateParamSupplied(parametersValues))
            {
                businessDate = parametersValues.get(1);
                setBiTemporaDates(milestoningDateParameters);
            }
            else if (isSingleDateTemporal(sourceTypeMilestoning) && oneDateParamSupplied(parametersValues))
            {
                int propagatedDateIndex = Objects.requireNonNull(sourceTypeMilestoning).positionInTemporalParameterValues();
                CoreInstance propagatedDate;
                if (isProcessingTemporal(sourceTypeMilestoning))
                {
                    propagatedDate = processingDate;
                }
                else
                {
                    propagatedDate = businessDate;
                }
                int otherPropagatedDateIndex = (sourceTypeMilestoning.getPurePlatformStereotypeName() == "processingtemporal") ? 1 : 0;
                setMilestoningDateParameters(milestoningDateParameters, propagatedDateIndex, propagatedDate);
                setMilestoningDateParameters(milestoningDateParameters, otherPropagatedDateIndex, parametersValues.get(1));
            }
            if (isBiTemporal(sourceTypeMilestoning) && noDateParamSupplied(parametersValues))
            {
                setBiTemporaDates(milestoningDateParameters);
            }
            if(milestoningDateParameters[0] == null || milestoningDateParameters[1] == null)
            {
                throw new EngineException("No-Arg milestoned property: '" + propertyName + "' must be either called in a milestoning context or supplied with " + "[processingDate, businessDate]" + " parameters", sourceInformation, EngineErrorType.COMPILATION);
            }
        }
        else if (isSingleDateTemporal(targetTypeMilestoning) && noDateParamSupplied(parametersValues))
        {
            CoreInstance propagatedDate;
            if (isProcessingTemporal(targetTypeMilestoning))
            {
                propagatedDate = processingDate;
            }
            else
            {
                propagatedDate = businessDate;
            }
            if (isBiTemporal(sourceTypeMilestoning))
            {
                setMilestoningDateParameters(milestoningDateParameters, 0, propagatedDate);
            }
            if (sourceTypeMilestoning == targetTypeMilestoning)
            {
                setMilestoningDateParameters(milestoningDateParameters, 0, propagatedDate);
            }
            if (milestoningDateParameters[0] == null)
            {
                if (isProcessingTemporal(targetTypeMilestoning))
                {
                    throw new EngineException("No-Arg milestoned property: '" + propertyName + "' must be either called in a milestoning context or supplied with " + "[processingDate]" + " parameters", sourceInformation, EngineErrorType.COMPILATION);
                }
                else
                {
                    throw new EngineException("No-Arg milestoned property: '" + propertyName + "' must be either called in a milestoning context or supplied with " + "[businessDate]" + " parameters", sourceInformation, EngineErrorType.COMPILATION);
                }
            }
        }
        if (!ArrayIterate.isEmpty(milestoningDateParameters))
        {
            parametersValues = LazyIterate.concatenate(FastList.<ValueSpecification>newListWith(parametersValues.get(0)), FastList.newListWith(milestoningDateParameters)).toList();
            fe._parametersValues(parametersValues);
        }
    }

    private static void updateFunctionExpressionWithMilestoningDateParams(FunctionExpression functionExpression, CoreInstance propertyFunc, SourceInformation sourceInformation)
    {
        applyPropertyFunctionExpressionMilestonedDates(functionExpression, propertyFunc, sourceInformation);
        InstanceValue propertyName = functionExpression._propertyName();
        functionExpression._propertyNameRemove();
        functionExpression._qualifiedPropertyName(propertyName);
    }

    static final Function<org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.Function, String> TO_FUNCTION_NAME = new Function<org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.Function, String>()
    {
        @Override
        public String valueOf(org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.Function coreInstance)
        {
            return coreInstance._functionName();
        }
    };

    public static CoreInstance getMilestoningQualifiedPropertyWithAllDatesSupplied(FunctionExpression functionExpression, CoreInstance propertyFunc, CompileContext context, Integer parametersCount, SourceInformation sourceInformation)
    {
        ListIterable<? extends ValueSpecification> parametersValues = functionExpression._parametersValues().toList();
        ValueSpecification source = parametersValues.get(0);
//        if (processingDate != null || businessDate != null)
//        {
            updateFunctionExpressionWithMilestoningDateParams(functionExpression, propertyFunc, sourceInformation);
//        }
        return functionExpression._func();
    }

    public static void updateMilestonedParameters(FunctionExpression functionExpression, CoreInstance propertyFunc, CompileContext context)
    {
        ListIterable<? extends ValueSpecification> parametersValues = functionExpression._parametersValues().toList();
        Class target = propertyFunc instanceof AbstractProperty ? (Class) ((AbstractProperty)propertyFunc)._genericType()._rawType() : null;
        MilestoningStereotype milestoningStereotype = Milestoning.temporalStereotypes(target._stereotypes()).get(0);
        if (isBiTemporal(milestoningStereotype))
        {
            processingDate = parametersValues.get(1);
            businessDate = parametersValues.get(2);
        }
        else if (isProcessingTemporal(milestoningStereotype))
        {
            processingDate = parametersValues.get(1);
        }
        else
        {
            businessDate = parametersValues.get(1);
        }
    }
}
