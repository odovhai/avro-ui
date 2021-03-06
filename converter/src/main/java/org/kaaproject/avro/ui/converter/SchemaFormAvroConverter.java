/*
 * Copyright 2014-2015 CyberVision, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kaaproject.avro.ui.converter;

/*
 * Copyright 2014-2015 CyberVision, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Field;
import org.apache.avro.Schema.Type;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericData.Record;
import org.apache.avro.generic.GenericData.StringType;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericRecordBuilder;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.BooleanNode;
import org.codehaus.jackson.node.DoubleNode;
import org.codehaus.jackson.node.IntNode;
import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.LongNode;
import org.codehaus.jackson.node.ObjectNode;
import org.codehaus.jackson.node.TextNode;
import org.kaaproject.avro.ui.shared.Base64Utils;
import org.kaaproject.avro.ui.shared.FieldType;
import org.kaaproject.avro.ui.shared.FormField;
import org.kaaproject.avro.ui.shared.FormField.FieldAccess;
import org.kaaproject.avro.ui.shared.Fqn;
import org.kaaproject.avro.ui.shared.FqnVersion;
import org.kaaproject.avro.ui.shared.NamesValidator;
import org.kaaproject.avro.ui.shared.RecordField;

/**
 * The Class SchemaFormAvroConverter.
 */
public class SchemaFormAvroConverter implements ConverterConstants, SchemaFormConstants {
    
    /** The Constant BASE_SCHEMA_FORM_SCHEMA_FILE. */
    private static final String BASE_SCHEMA_FORM_SCHEMA_FILE = "schema-record.avsc";
    
    /** The base schema form schema. */
    private static Schema baseSchemaFormSchema;
    
    /**
     * Gets the base schema form schema.
     *
     * @return the base schema form schema
     * @throws IOException Signals that an I/O exception has occurred.
     */
    protected static Schema getBaseSchemaFormSchema() throws IOException {
        if (baseSchemaFormSchema == null) {
            baseSchemaFormSchema = new Schema.Parser().parse(Thread.currentThread().getContextClassLoader().
                    getResourceAsStream(BASE_SCHEMA_FORM_SCHEMA_FILE));
        }
        return baseSchemaFormSchema;
    }
    
    /** The schema form schema. */
    private Schema schemaFormSchema;
    
    /** The ctl source. */
    private CtlSource ctlSource;
    
    /** The has ctl. */
    private boolean hasCtl = false;
    
    /**
     * Instantiates a new schema form avro converter.
     *
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public SchemaFormAvroConverter() throws IOException {
        this(null);
    }
    
    /**
     * Instantiates a new schema form avro converter.
     *
     * @param ctlSource the ctl source
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public SchemaFormAvroConverter(CtlSource ctlSource) throws IOException {
        this.ctlSource = ctlSource;
        this.hasCtl = this.ctlSource != null;
        this.schemaFormSchema = createConverterSchema();
    }
    
    /**
     * Gets the empty schema form instance.
     *
     * @return the empty schema form instance
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public RecordField getEmptySchemaFormInstance() throws IOException {
        RecordField schemaForm = FormAvroConverter.createRecordFieldFromSchema(schemaFormSchema, ctlSource);
        schemaForm.finalizeMetadata();
        return customizeUiForm(customizeUiFormForCtl(schemaForm));
    }
    
    /**
     * Creates the schema form from schema.
     *
     * @param schemaString the schema string
     * @return the record field
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public RecordField createSchemaFormFromSchema(String schemaString) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(schemaString);
        Schema.Parser parser = new Schema.Parser();
        Set<Fqn> fqns = null;
        if (hasCtl) {
            JsonNode dependenciesNode = node.get(DEPENDENCIES);
            if (dependenciesNode != null && dependenciesNode.isArray()) {
                Map<String,Schema> types = new HashMap<>();
                fqns = new HashSet<>();
                for (int i=0;i<dependenciesNode.size();i++) {
                    JsonNode dependencyNode = dependenciesNode.get(i);
                    Fqn fqn = new Fqn(dependencyNode.get(FQN).asText());
                    types.put(fqn.getFqnString(), Schema.createRecord(fqn.getName(), null, fqn.getNamespace(), false));
                    fqns.add(fqn);
                }
                parser.addTypes(types);
            }
        }
        Schema schema = parser.parse(schemaString);
        return createSchemaFormFromSchema(schema, fqns);
    }
    
    /**
     * Creates the schema form from schema.
     *
     * @param schema the schema
     * @return the record field
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public RecordField createSchemaFormFromSchema(Schema schema) throws IOException {
        return createSchemaFormFromSchema(schema, null);
    }
    
    /**
     * Creates the schema form from schema.
     *
     * @param schema the schema
     * @param fqns the fqns
     * @return the record field
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public RecordField createSchemaFormFromSchema(Schema schema, Set<Fqn> fqns) throws IOException {
        String namespace = "";
        if (schema.getType() == Schema.Type.RECORD) {
            namespace = schema.getNamespace();
        }
        GenericRecord record = (GenericRecord) createTypeFromSchema(schema, fqns, true, namespace);
        RecordField recordField = FormAvroConverter.createRecordFieldFromGenericRecord(record, ctlSource);
        customizeUiForm(customizeUiFormForCtl(recordField));
        return recordField;
    }
    
    /**
     * Creates the schema from schema form.
     *
     * @param field the field
     * @return the schema
     * @throws IOException Signals that an I/O exception has occurred.
     * @throws ParseException the parse exception
     */
    public Schema createSchemaFromSchemaForm(RecordField field) throws IOException, ParseException {
        field.orderSchemaTypes();
        GenericRecord record = FormAvroConverter.createGenericRecordFromRecordField(field);
        Map<Fqn, Schema> namedSchemas = null;
        if (hasCtl) {
            namedSchemas = new HashMap<>();
            List<FqnVersion> dependencies = field.getContext().getCtlDependenciesList();
            for (FqnVersion fqnVersion : dependencies) {
                Fqn fqn = fqnVersion.getFqn();
                Schema emptyRecordSchema = Schema.createRecord(fqnVersion.getName(), null, fqnVersion.getNamespace(), false);
                emptyRecordSchema.setFields(Collections.<Field>emptyList());
                namedSchemas.put(fqn, emptyRecordSchema);
            }
        }
        String rootNamespace = "";
        if (field.getContext().getRootRecord().getDeclaredFqn() != null) {
            rootNamespace = field.getContext().getRootRecord().getDeclaredFqn().getNamespace();
        }
        Schema schema = createFieldSchema(record, namedSchemas, rootNamespace);
        return schema;
    }
    
    /**
     * Creates the schema string.
     *
     * @param schema the schema
     * @param pretty the pretty
     * @return the string
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public static String createSchemaString(Schema schema, boolean pretty) throws IOException {
        Schema holderSchema = Schema.createRecord(SchemaFormAvroConverter.class.getSimpleName(), 
                null, SchemaFormAvroConverter.class.getPackage().getName(), false);
        List<Field> fields = new ArrayList<Field>();
        JsonNode dependenciesNode = schema.getJsonProp(DEPENDENCIES);
        if (dependenciesNode != null && dependenciesNode.isArray()) {
            for (int i=0;i<dependenciesNode.size();i++) {
                JsonNode dependencyNode = dependenciesNode.get(i);
                String fqn = dependencyNode.get(FQN).asText();
                Schema fieldType = findType(schema, fqn, null);
                if (fieldType != null) {
                    Field tempField =  new Field(fqn.replaceAll("\\.", "_"), fieldType, null, null);
                    fields.add(tempField);
                }
            }
        }        
        Field holdedField = new Field(HOLDED_SCHEMA_FIELD, schema, null, null);
        fields.add(holdedField);        
        holderSchema.setFields(fields);
        String schemaString = holderSchema.toString();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(schemaString);
        ArrayNode fieldsNode = (ArrayNode)node.get(FIELDS);
        JsonNode fieldNode = fieldsNode.get(fields.size()-1);
        JsonNode typeNode = fieldNode.get(TYPE);
        if (pretty) {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(typeNode);
        } else {
            return mapper.writeValueAsString(typeNode);
        }
    }
    
    /**
     * Find type.
     *
     * @param schema the schema
     * @param fqn the fqn
     * @param fqns the fqns
     * @return the schema
     */
    private static Schema findType(Schema schema, String fqn, Set<String> fqns) {
        if (fqns == null) {
            fqns = new HashSet<>();
        }
        Schema type = null;
        switch (schema.getType()) {
        case ARRAY:
            type = findType(schema.getElementType(), fqn, fqns);
            break;
        case RECORD:
            type = findTypeFromRecordSchema(schema, fqn, fqns);
            break;
        case UNION:
            for (Schema schemaType : schema.getTypes()) {
                type = findType(schemaType, fqn, fqns);
                if (type != null) {
                    return type;
                }
            }
            break;
        default:
            break;
        }
        return type;
    }
    
    /**
     * Find type from record schema.
     *
     * @param recordSchema the record schema
     * @param fqn the fqn
     * @param fqns the fqns
     * @return the schema
     */
    private static Schema findTypeFromRecordSchema (Schema recordSchema, String fqn, Set<String> fqns) {        
        if (recordSchema.getFullName().equals(fqn)) {
            return recordSchema;
        }
        if (fqns.contains(recordSchema.getFullName())) {
            return null;
        } else {
            Schema type = null;
            fqns.add(recordSchema.getFullName());
            for (Field field : recordSchema.getFields()) {
                type = findType(field.schema(), fqn, fqns);
                if (type != null) {
                    return type;
                }
            }
            return type;
        }
    }
    
    /**
     * Creates the converter schema.
     *
     * @return the schema
     * @throws IOException Signals that an I/O exception has occurred.
     */
    protected Schema createConverterSchema() throws IOException {
        Schema initialSchema = getBaseSchemaFormSchema();
        Map<String, Schema> recordSchemaMap = new HashMap<>();
        return copySchema(initialSchema, recordSchemaMap);
    }
    
    /**
     * Customize type.
     *
     * @param record the record
     * @param fieldTypeSchema the field type schema
     */
    protected void customizeType(Record record, Schema fieldTypeSchema) {
    }

    /**
     * Customize form field.
     *
     * @param fieldType the field type
     * @param field the field
     */
    protected void customizeFormField(Record fieldType, Field field) {
    }

    /**
     * Customize field schema.
     *
     * @param fieldSchema the field schema
     * @param fieldType the field type
     */
    protected void customizeFieldSchema(Schema fieldSchema, GenericRecord fieldType) {
    }

    /**
     * Customize schema field.
     *
     * @param avroField the avro field
     * @param fieldType the field type
     */
    protected void customizeSchemaField(Field avroField, Record fieldType) {
    }

    /**
     * Customize record fields.
     *
     * @param recordSchema the record schema
     * @param fields the fields
     */
    protected void customizeRecordFields(Schema recordSchema, List<Field> fields) {
    }
    
    /**
     * Creates the version field.
     *
     * @return the field
     */
    private Field createVersionField() {
        Field versionField = new Field(VERSION, Schema.createUnion(Arrays.asList(
                Schema.create(Type.INT), Schema.create(Type.NULL))), null, null);
        versionField.addProp(DISPLAY_NAME, "Version");
        versionField.addProp(DISPLAY_PROMPT, "Enter type version");
        versionField.addProp(TYPE_VERSION, BooleanNode.valueOf(true));
        versionField.addProp(FIELD_ACCESS, FieldAccess.HIDDEN.name().toLowerCase());
        return versionField;
    }
    
    /**
     * Creates the dependencies field.
     *
     * @return the field
     */
    private Field createDependenciesField() {
        Schema dependencyType = Schema.createRecord(DEPENDENCY_FIELD_TYPE, null, BASE_SCHEMA_FORM_NAMESPACE, false);
        Field fqnField = new Field(FQN, Schema.create(Type.STRING), null, null);
        Field versionField = new Field(VERSION, Schema.create(Type.INT), null, null);
        dependencyType.setFields(Arrays.asList(fqnField, versionField));
        Schema dependenciesArray = Schema.createArray(dependencyType);
        Field dependenciesField = new Field(DEPENDENCIES, Schema.createUnion(Arrays.asList(
                dependenciesArray, Schema.create(Type.NULL))), null, null);
        dependenciesField.addProp(DISPLAY_NAME, "Dependencies");
        dependenciesField.addProp(TYPE_DEPENDENCIES, BooleanNode.valueOf(true));
        dependenciesField.addProp(FIELD_ACCESS, FieldAccess.HIDDEN.name().toLowerCase());
        return dependenciesField;
    }

    /**
     * Customize ui form.
     *
     * @param field the field
     * @return the record field
     */
    protected RecordField customizeUiForm(RecordField field) {
        field.setDisplayName(SCHEMA);
        return field;
    }
    
    /**
     * Customize ui form for ctl.
     *
     * @param field the field
     * @return the record field
     */
    private RecordField customizeUiFormForCtl(RecordField field) {
        if (hasCtl) {
            FormField versionField = field.getFieldByName(VERSION);
            if (versionField != null) {
                versionField.setFieldAccess(FieldAccess.EDITABLE);
                versionField.setOptional(false);
            }
            FormField dependenciesField = field.getFieldByName(DEPENDENCIES);
            if (dependenciesField != null) {
                dependenciesField.setFieldAccess(FieldAccess.EDITABLE);
            }
        }
        return field;
    }
    
    /**
     * Gets the field index.
     *
     * @param fields the fields
     * @param name the name
     * @return the field index
     */
    protected int getFieldIndex(List<Field> fields, String name) {
        int index = -1;
        for (int i=0; i<fields.size();i++) {
            if (fields.get(i).name().equals(name)) {
                index = i;
                break;
            }
        }
        return index;
    }

    /**
     * Copy schema.
     *
     * @param schema the schema
     * @param recordSchemaMap the record schema map
     * @return the schema
     */
    protected Schema copySchema(Schema schema, Map<String, Schema> recordSchemaMap) {
        Schema schemaCopy = null;
        switch (schema.getType()) {
        case ARRAY:
            schemaCopy = Schema.createArray(copySchema(schema.getElementType(), recordSchemaMap));
            break;
        case BOOLEAN:
            schemaCopy = Schema.create(Type.BOOLEAN);
            break;
        case BYTES:
            schemaCopy = Schema.create(Type.BYTES);
            break;
        case DOUBLE:
            schemaCopy = Schema.create(Type.DOUBLE);
            break;
        case ENUM:
            schemaCopy = Schema.createEnum(schema.getName(), null,
                    schema.getNamespace(), schema.getEnumSymbols());
            break;
        case FIXED:
            schemaCopy = Schema.createFixed(schema.getName(), null,
                    schema.getNamespace(), schema.getFixedSize());
            break;
        case FLOAT:
            schemaCopy = Schema.create(Type.FLOAT);
            break;
        case INT:
            schemaCopy = Schema.create(Type.INT);
            break;
        case LONG:
            schemaCopy = Schema.create(Type.LONG);
            break;
        case NULL:
            schemaCopy = Schema.create(Type.NULL);
            break;
        case RECORD:
            schemaCopy = copyRecordSchema(schema, recordSchemaMap);
            break;
        case STRING:
            schemaCopy = Schema.create(Type.STRING);
            break;
        case UNION:
            List<Schema> types = new ArrayList<>();
            for (Schema type : schema.getTypes()) {
                types.add(copySchema(type, recordSchemaMap));
            }
            schemaCopy = Schema.createUnion(types);
            break;
        default:
            throw new UnsupportedOperationException("Unsupported avro type: " + schema.getType());
        }
        return schemaCopy;
    }
    
    /**
     * Copy record schema.
     *
     * @param recordSchema the record schema
     * @param recordSchemaMap the record schema map
     * @return the schema
     */
    private Schema copyRecordSchema(Schema recordSchema, Map<String, Schema> recordSchemaMap) {
        if (recordSchemaMap.containsKey(recordSchema.getFullName())) {
            return recordSchemaMap.get(recordSchema.getFullName());
        } else {
            Schema recordSchemaCopy = Schema.createRecord(recordSchema.getName(), null, recordSchema.getNamespace(), false);
            recordSchemaMap.put(recordSchemaCopy.getFullName(), recordSchemaCopy);
            Map<String, JsonNode> props = recordSchema.getJsonProps();
            for (String key : props.keySet()) {
                recordSchemaCopy.addProp(key, props.get(key));
            }
            List<Field> recordFieldsCopy = new ArrayList<>();
            for (Field field : recordSchema.getFields()) {
                recordFieldsCopy.add(copySchemaField(field, recordSchemaMap));
            }
            if (hasCtl) {
                if (recordSchema.getName().equals(RECORD_FIELD_TYPE)) {
                    int index = getFieldIndex(recordFieldsCopy, RECORD_NAMESPACE);
                    if (index > -1) {
                        recordFieldsCopy.add(index+1, createVersionField());
                    }
                    index = getFieldIndex(recordFieldsCopy, FIELDS);
                    if (index > -1) {
                        recordFieldsCopy.add(index, createDependenciesField());
                    }
                } 
            }
            customizeRecordFields(recordSchema, recordFieldsCopy);
            
            recordSchemaCopy.setFields(recordFieldsCopy);
            return recordSchemaCopy;
        }
    }
    
    /**
     * Copy schema field.
     *
     * @param field the field
     * @param recordSchemaMap the record schema map
     * @return the field
     */
    private Field copySchemaField(Field field, Map<String, Schema> recordSchemaMap) {
        Field fieldCopy = new Field(field.name(), copySchema(field.schema(), recordSchemaMap), null, null);
        Map<String, JsonNode> props = field.getJsonProps();
        for (String key : props.keySet()) {
            fieldCopy.addProp(key, props.get(key));
        }
        return fieldCopy;
    }

    /**
     * Find type schema.
     *
     * @param rootSchema the root schema
     * @param typeName the type name
     * @return the schema
     */
    private Schema findTypeSchema(Schema rootSchema, String typeName) {
        List<Schema> types = null;
        if (rootSchema.getName().equals(RECORD_FIELD_TYPE)) {
            types = rootSchema.getField(FIELDS).schema().getElementType().getField(FIELD_TYPE).schema().getTypes();
        } else if (rootSchema.getName().equals(UNION_FIELD_TYPE)) {
            types = rootSchema.getField(ACCEPTABLE_VALUES).schema().getElementType().getTypes();
        } else {
            throw new IllegalArgumentException("Ivalid schema form conversion schema: " + rootSchema);
        }
        for (Schema type : types) {
            if (type.getName().equals(typeName)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid type name: " + typeName);
    }
    
    /**
     * Creates the type from schema.
     *
     * @param schema the schema
     * @param namedFqns the named fqns
     * @return the record
     */
    private Record createTypeFromSchema(Schema schema, Set<Fqn> namedFqns, boolean isRoot, String rootNamespace) {
        if (namedFqns == null) {
            namedFqns = new HashSet<>();
        }
        Schema fieldTypeSchema = FormAvroConverter.getFieldTypeSchema(schema);
        
        Type type = fieldTypeSchema.getType();
        Record record;
        switch (type) {
            case STRING:
                record = new Record(findTypeSchema(schemaFormSchema, STRING_FIELD_TYPE));
                break;
            case INT:
                record = new Record(findTypeSchema(schemaFormSchema, INTEGER_FIELD_TYPE));
                break;
            case LONG:
                record = new Record(findTypeSchema(schemaFormSchema, LONG_FIELD_TYPE));
                break;
            case FLOAT:
                record = new Record(findTypeSchema(schemaFormSchema, FLOAT_FIELD_TYPE));
                break;
            case DOUBLE:
                record = new Record(findTypeSchema(schemaFormSchema, DOUBLE_FIELD_TYPE));
                break;
            case BOOLEAN:
                record = new Record(findTypeSchema(schemaFormSchema, BOOLEAN_FIELD_TYPE));
                break;
            case BYTES:
                record = new Record(findTypeSchema(schemaFormSchema, BYTES_FIELD_TYPE));
                break;
            case FIXED:
            case ENUM:
            case RECORD:
                Fqn fqn = new Fqn(fieldTypeSchema.getNamespace(), fieldTypeSchema.getName());
                if (namedFqns.contains(fqn)) {
                    Schema namedReferenceTypeSchema = findTypeSchema(schemaFormSchema, NAMED_REFERENCE_FIELD_TYPE);
                    record = new Record(namedReferenceTypeSchema);
                    record.put(FQN, fqn.getFqnString());           
                } else {
                    NamesValidator.validateFqnOrThrowException(fqn);
                    namedFqns.add(fqn);
                    if (type == Type.FIXED) {
                        Schema fixedTypeSchema = findTypeSchema(schemaFormSchema, FIXED_FIELD_TYPE);
                        record = new Record(fixedTypeSchema);
                        record.put(FIXED_SIZE, fieldTypeSchema.getFixedSize());
                    } else if (type == Type.ENUM) {
                        Schema enumTypeSchema = findTypeSchema(schemaFormSchema, ENUM_FIELD_TYPE);
                        record = new Record(enumTypeSchema);
                        Field symbolsField = enumTypeSchema.getField(SYMBOLS);
                        
                        List<String> symbols = fieldTypeSchema.getEnumSymbols();
                        GenericData.Array<Record> symbolsArray = new GenericData.Array<>(symbols.size(), symbolsField.schema());
                        
                        Schema enumSymbolSchema = symbolsField.schema().getElementType();
                        
                        for (String symbol : symbols) {
                            NamesValidator.validateEnumSymbolOrThrowException(symbol);
                            Record enumSymbolRecord = new Record(enumSymbolSchema);
                            enumSymbolRecord.put(SYMBOL, symbol);
                            symbolsArray.add(enumSymbolRecord);
                        }
                        record.put(SYMBOLS, symbolsArray);
                    } else {
                        Schema recordTypeSchema = findTypeSchema(schemaFormSchema, RECORD_FIELD_TYPE);
                        record = new Record(recordTypeSchema);
                        Field fieldsField = recordTypeSchema.getField(FIELDS);
                        List<Field> fields = fieldTypeSchema.getFields();
                        GenericData.Array<Object> fieldsArrayData = new GenericData.Array<>(fields.size(), fieldsField.schema());
                        
                        Schema fieldSchema = fieldsField.schema().getElementType();
                        java.util.Set<String> fieldNames = new java.util.HashSet<>();
                        
                        for (Field field : fields) {
                            String fieldName = field.name().toLowerCase();
                            if (!fieldNames.contains(fieldName)) {
                                fieldNames.add(fieldName);
                            } else {
                                throw new IllegalArgumentException("Duplicate field name: " + fieldName);
                            }

                            fieldsArrayData.add(createFormFieldFromSchemaField(fieldSchema, field, namedFqns, rootNamespace));
                        }
                        record.put(FIELDS, fieldsArrayData);

                        if (record.getSchema().getField(DISPLAY_NAME) != null) {
                            JsonNode displayNameNode = fieldTypeSchema.getJsonProp(DISPLAY_NAME);
                            if (displayNameNode != null && displayNameNode.isTextual()) {
                                record.put(DISPLAY_NAME, displayNameNode.asText());
                            }                    
                        }
                        
                        if (record.getSchema().getField(DESCRIPTION) != null) {
                            JsonNode descriptionNode = fieldTypeSchema.getJsonProp(DESCRIPTION);
                            if (descriptionNode != null && descriptionNode.isTextual()) {
                                record.put(DESCRIPTION, descriptionNode.asText());
                            }                    
                        }
                        
                        if (hasCtl) {
                            JsonNode versionNode = fieldTypeSchema.getJsonProp(VERSION);
                            if (versionNode != null && versionNode.isInt()) {
                                record.put(VERSION, versionNode.asInt());
                            }
                            JsonNode dependenciesNode = fieldTypeSchema.getJsonProp(DEPENDENCIES);
                            if (dependenciesNode != null && dependenciesNode.isArray()) {
                                Field dependenciesField = recordTypeSchema.getField(DEPENDENCIES);
                                Schema dependenciesFieldSchema = dependenciesField.schema();
                                int index = dependenciesFieldSchema.getIndexNamed(FieldType.ARRAY.getName());
                                Schema dependenciesSchema = dependenciesFieldSchema.getTypes().get(index);
                                GenericData.Array<Record> dependenciesArrayData = 
                                        new GenericData.Array<>(dependenciesNode.size(), dependenciesSchema);
                                Schema dependencySchema = dependenciesSchema.getElementType();
                                for (int i=0; i<dependenciesNode.size();i++) {
                                    JsonNode dependencyNode = dependenciesNode.get(i);
                                    GenericRecordBuilder builder = new GenericRecordBuilder(dependencySchema);
                                    Field field = dependencySchema.getField(FQN);
                                    builder.set(field, dependencyNode.get(FQN).asText());
                                    field = dependencySchema.getField(VERSION);
                                    builder.set(field, dependencyNode.get(VERSION).asInt());
                                    dependenciesArrayData.add(builder.build());
                                }
                                record.put(DEPENDENCIES, dependenciesArrayData);
                            }
                        }                        
                    }
                    record.put(RECORD_NAME, fieldTypeSchema.getName());
                    String namespace = fieldTypeSchema.getNamespace();
                    if (!isRoot && rootNamespace.equals(namespace)) {
                        namespace = null;
                    }
                    record.put(RECORD_NAMESPACE, namespace);
                }
                break;
            case ARRAY:
                Schema arrayTypeSchema = findTypeSchema(schemaFormSchema, ARRAY_FIELD_TYPE);
                record = new Record(arrayTypeSchema);
                Record arrayItemRecord = createTypeFromSchema(fieldTypeSchema.getElementType(), namedFqns, false, rootNamespace);
                record.put(ARRAY_ITEM, arrayItemRecord);
                break;
            case UNION:
                Schema unionTypeSchema = findTypeSchema(schemaFormSchema, UNION_FIELD_TYPE);
                record = new Record(unionTypeSchema);
                
                Field acceptableValuesField = unionTypeSchema.getField(ACCEPTABLE_VALUES);
                List<Schema> types = fieldTypeSchema.getTypes();
                GenericData.Array<Record> acceptableValuesArrayData = new GenericData.Array<>(types.size(), acceptableValuesField.schema());
                for (Schema typeSchema : types) {
                    if (typeSchema.getType() != Schema.Type.NULL) {
                        Record fieldTypeRecord = createTypeFromSchema(typeSchema, namedFqns, false, rootNamespace);
                        acceptableValuesArrayData.add(fieldTypeRecord);
                    }
                }
                record.put(ACCEPTABLE_VALUES, acceptableValuesArrayData);
                break;
            case NULL:
                record = null;
                break;
            default:
                throw new UnsupportedOperationException("Unsupported avro field type: " + type);
        }
        customizeType(record, fieldTypeSchema);
        return record;
    }
    
    /**
     * Creates the form field from schema field.
     *
     * @param recordTypeFieldSchema the record type field schema
     * @param field the field
     * @param namedFqns the named fqns
     * @return the record
     */
    private Record createFormFieldFromSchemaField(Schema recordTypeFieldSchema, Field field, Set<Fqn> namedFqns, String rootNamespace) {
        Record fieldRecord = new Record(recordTypeFieldSchema);        
        Schema fieldSchema = field.schema();
        if (fieldRecord.getSchema().getField(OPTIONAL) != null) {
            fieldRecord.put(OPTIONAL, FormAvroConverter.isNullTypeSchema(fieldSchema));
        }
        if (fieldRecord.getSchema().getField(FIELD_NAME) != null) {
            fieldRecord.put(FIELD_NAME, field.name());
        }
        
        if (fieldRecord.getSchema().getField(DISPLAY_NAME) != null) {
            JsonNode displayNameNode = field.getJsonProp(DISPLAY_NAME);
            if (displayNameNode != null && displayNameNode.isTextual()) {
                fieldRecord.put(DISPLAY_NAME, displayNameNode.asText());
            }
        }
        
        if (fieldRecord.getSchema().getField(DESCRIPTION) != null) {
            JsonNode descriptionNode = field.getJsonProp(DESCRIPTION);
            if (descriptionNode != null && descriptionNode.isTextual()) {
                fieldRecord.put(DESCRIPTION, descriptionNode.asText());
            }
        }
        
        if (fieldRecord.getSchema().getField(DISPLAY_PROMPT) != null) {
            JsonNode displayPromptNode = field.getJsonProp(DISPLAY_PROMPT);
            if (displayPromptNode != null && displayPromptNode.isTextual()) {
                fieldRecord.put(DISPLAY_PROMPT, displayPromptNode.asText());
            }
        }
        
        if (fieldRecord.getSchema().getField(WEIGHT) != null) {
            JsonNode weightNode = field.getJsonProp(WEIGHT);
            if (weightNode != null && weightNode.isFloatingPointNumber()) {
                fieldRecord.put(WEIGHT, new Float(weightNode.asDouble()));
            }
        }
        
        if (fieldRecord.getSchema().getField(KEY_INDEX) != null) {
            JsonNode keyIndexNode = field.getJsonProp(KEY_INDEX);
            if (keyIndexNode != null && keyIndexNode.isInt()) {
                fieldRecord.put(KEY_INDEX, keyIndexNode.asInt());
            }
        }
        
        Record fieldType = createTypeFromSchema(field.schema(), namedFqns, false, rootNamespace);
        
        if (fieldType.getSchema().getField(DEFAULT_VALUE) != null) {
            JsonNode defaultValueNode = field.getJsonProp(BY_DEFAULT);
            if (defaultValueNode != null) {
                setDefaultValueFromJsonNode(fieldType, defaultValueNode);
            }
        }
        
        String fieldTypeName = fieldType.getSchema().getName();
        
        if (fieldTypeName.equals(STRING_FIELD_TYPE)) {
            if (fieldType.getSchema().getField(MAX_LENGTH) != null) {
                JsonNode maxLengthNode = field.getJsonProp(MAX_LENGTH);
                if (maxLengthNode != null && maxLengthNode.isInt()) {
                    fieldType.put(MAX_LENGTH, maxLengthNode.asInt());
                }
            }
            if (fieldType.getSchema().getField(INPUT_TYPE) != null) {
                JsonNode inputTypeNode = field.getJsonProp(INPUT_TYPE);
                if (inputTypeNode != null && inputTypeNode.isTextual()) {
                    Schema inputTypeSchema = fieldType.getSchema().getField(INPUT_TYPE).schema();
                    fieldType.put(INPUT_TYPE, 
                            new GenericData.EnumSymbol(inputTypeSchema, inputTypeNode.asText().toUpperCase()));
                }
            }
        } else if (fieldTypeName.equals(ENUM_FIELD_TYPE)) {
            JsonNode displayNamesNode = field.getJsonProp(DISPLAY_NAMES);
            
            @SuppressWarnings("unchecked")
            GenericData.Array<Record> genericArrayData = 
                    (GenericData.Array<Record>) fieldType.get(SYMBOLS);
            
            if (displayNamesNode != null && displayNamesNode.isArray() && 
                    displayNamesNode.size() == genericArrayData.size()) {
                for (int i=0; i<genericArrayData.size();i++) {
                    if (genericArrayData.get(i).getSchema().getField(DISPLAY_NAME) != null) {
                        String displayName = displayNamesNode.get(i).asText();
                        genericArrayData.get(i).put(DISPLAY_NAME, displayName);
                    }
                }
            }
        } else if (fieldTypeName.equals(ARRAY_FIELD_TYPE)) {
            if (fieldType.getSchema().getField(MIN_ROW_COUNT) != null) {
                JsonNode minRowCountNode = field.getJsonProp(MIN_ROW_COUNT);
                if (minRowCountNode != null && minRowCountNode.isInt()) {
                    fieldType.put(MIN_ROW_COUNT, minRowCountNode.asInt());
                }
            }
        }
        customizeFormField(fieldType, field);
        fieldRecord.put(FIELD_TYPE, fieldType);
        return fieldRecord;
    }
    
    /**
     * Sets the default value from json node.
     *
     * @param fieldType the field type
     * @param defaultValueNode the default value node
     */
    private static void setDefaultValueFromJsonNode(Record fieldType, JsonNode defaultValueNode) {
        if (fieldType.getSchema().getName().equals(STRING_FIELD_TYPE)) {
            if (defaultValueNode.isTextual()) {
                    fieldType.put(DEFAULT_VALUE, defaultValueNode.asText());
            }
        } else if (fieldType.getSchema().getName().equals(INTEGER_FIELD_TYPE)) {
            if (defaultValueNode.isNumber()) {
                fieldType.put(DEFAULT_VALUE, defaultValueNode.asInt());
            }
        } else if (fieldType.getSchema().getName().equals(LONG_FIELD_TYPE)) {
            if (defaultValueNode.isNumber()) {
                fieldType.put(DEFAULT_VALUE, defaultValueNode.asLong());
            }
        } else if (fieldType.getSchema().getName().equals(FLOAT_FIELD_TYPE)) {
            if (defaultValueNode.isFloatingPointNumber()) {
                fieldType.put(DEFAULT_VALUE, new Float(defaultValueNode.asDouble()));
            }
        } else if (fieldType.getSchema().getName().equals(DOUBLE_FIELD_TYPE)) {
            if (defaultValueNode.isFloatingPointNumber()) {
                fieldType.put(DEFAULT_VALUE, defaultValueNode.asDouble());
            }
        } else if (fieldType.getSchema().getName().equals(BOOLEAN_FIELD_TYPE)) {
            if (defaultValueNode.isBoolean()) {
                fieldType.put(DEFAULT_VALUE, defaultValueNode.asBoolean());
            }
        } else if (fieldType.getSchema().getName().equals(BYTES_FIELD_TYPE)) {
            String val = parseBytesJsonValue(defaultValueNode);
            fieldType.put(DEFAULT_VALUE, val);
        } else if (fieldType.getSchema().getName().equals(FIXED_FIELD_TYPE)) {
            String val = parseBytesJsonValue(defaultValueNode);
            fieldType.put(DEFAULT_VALUE, val);
        } else if (fieldType.getSchema().getName().equals(ENUM_FIELD_TYPE)) {
            if (defaultValueNode.isTextual()) {
                fieldType.put(DEFAULT_VALUE, defaultValueNode.asText());
            }
        } else if (fieldType.getSchema().getName().equals(UNION_FIELD_TYPE)) {
            if (defaultValueNode != null) {
                
                @SuppressWarnings("unchecked")
                GenericData.Array<Record> acceptableValuesArray = 
                        (GenericData.Array<Record>) fieldType.get(ACCEPTABLE_VALUES);
                
                Object val = convertUnionDefaultValueFromJson(acceptableValuesArray, defaultValueNode);
                fieldType.put(DEFAULT_VALUE, val);
            }
        }      
    }
    
    /**
     * Parses the bytes json value.
     *
     * @param jsonValue the json value
     * @return the string
     */
    private static String parseBytesJsonValue(JsonNode jsonValue) {
        if (jsonValue.isTextual() || jsonValue.isBinary()) {
            return jsonValue.asText();
        } else if (jsonValue.isArray()) {
            byte[] data = new byte[jsonValue.size()];
            for (int i=0;i<jsonValue.size();i++) {
                int val = jsonValue.get(i).asInt();
                data[i] = (byte) val;
            }
            return Base64Utils.toBase64(data);
        } else {
            return null;
        }
    }
    
    /**
     * Convert union default value from json.
     *
     * @param acceptableValuesArray the acceptable values array
     * @param jsonValue the json value
     * @return the record
     */
    private static Record convertUnionDefaultValueFromJson(GenericData.Array<Record> acceptableValuesArray, JsonNode jsonValue) {
        if (jsonValue == null) {
            return null;
        }
        for (Record type : acceptableValuesArray) {
            if (matchesType(type, jsonValue)) {
                if (type.getSchema().getName().equals(ENUM_FIELD_TYPE)) {
                    String val = jsonValue.asText();
                    @SuppressWarnings("unchecked")
                    GenericData.Array<Record> symbolsArray = (GenericData.Array<Record>) type.get(SYMBOLS);
                    boolean found = false;
                    for (Record enumSymbol : symbolsArray) {
                        String symbol = (String) enumSymbol.get(SYMBOL);
                        if (symbol.equals(val)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        continue;
                    }
                } 
                if (type.getSchema().getField(DEFAULT_VALUE) != null) {
                    setDefaultValueFromJsonNode(type, jsonValue);
                }
                return type;
            }
        }
        return null;
    }
    
    /**
     * Matches type.
     *
     * @param type the type
     * @param jsonValue the json value
     * @return true, if successful
     */
    private static boolean matchesType(Record type, JsonNode jsonValue) {
        if (type.getSchema().getName().equals(BOOLEAN_FIELD_TYPE)) {
            return jsonValue.isBoolean();
        } else if (type.getSchema().getName().equals(INTEGER_FIELD_TYPE)) {
            return jsonValue.isInt();
        } else if (type.getSchema().getName().equals(LONG_FIELD_TYPE)) {
            return jsonValue.isIntegralNumber();
        } else if (type.getSchema().getName().equals(FLOAT_FIELD_TYPE)) {
            return jsonValue.isDouble();
        } else if (type.getSchema().getName().equals(DOUBLE_FIELD_TYPE)) {
            return jsonValue.isFloatingPointNumber();
        } else if (type.getSchema().getName().equals(STRING_FIELD_TYPE) || 
                type.getSchema().getName().equals(ENUM_FIELD_TYPE)) {
            return jsonValue.isTextual();
        } else if (type.getSchema().getName().equals(BYTES_FIELD_TYPE) || 
                type.getSchema().getName().equals(FIXED_FIELD_TYPE)) {
            return jsonValue.isBinary() || jsonValue.isArray();
        } else {
            return false;
        }
    }
    
    /**
     * Creates the field schema.
     *
     * @param fieldType the field type
     * @param namedSchemas the named schemas
     * @param rootNamespace the root namespace
     * @return the schema
     * @throws ParseException the parse exception
     */
    private Schema createFieldSchema(GenericRecord fieldType, Map<Fqn, Schema> namedSchemas, String rootNamespace) throws ParseException {
        if (namedSchemas == null) {
            namedSchemas = new HashMap<>();
        }
        Schema fieldSchema = null;
        String fieldTypeName = null;
        if (fieldType != null) {
            fieldTypeName = fieldType.getSchema().getName();
        }
        if (fieldTypeName == null) {
            fieldSchema = Schema.create(Type.NULL);
        } else if (fieldTypeName.equals(STRING_FIELD_TYPE)) {
            fieldSchema = Schema.create(Type.STRING);
            GenericData.setStringType(fieldSchema, StringType.String);
        } else if (fieldTypeName.equals(INTEGER_FIELD_TYPE)) {
            fieldSchema = Schema.create(Type.INT);
        } else if (fieldTypeName.equals(LONG_FIELD_TYPE)) {
            fieldSchema = Schema.create(Type.LONG);
        } else if (fieldTypeName.equals(FLOAT_FIELD_TYPE)) {
            fieldSchema = Schema.create(Type.FLOAT);
        } else if (fieldTypeName.equals(DOUBLE_FIELD_TYPE)) {
            fieldSchema = Schema.create(Type.DOUBLE);
        } else if (fieldTypeName.equals(BOOLEAN_FIELD_TYPE)) {
            fieldSchema = Schema.create(Type.BOOLEAN);
        } else if (fieldTypeName.equals(BYTES_FIELD_TYPE)) {
            fieldSchema = Schema.create(Type.BYTES);
        } else if (fieldTypeName.equals(FIXED_FIELD_TYPE) ||
                fieldTypeName.equals(ENUM_FIELD_TYPE) ||
                fieldTypeName.equals(RECORD_FIELD_TYPE)) {
            String recordNamespace = (String) fieldType.get(RECORD_NAMESPACE);
            if (recordNamespace == null || recordNamespace.isEmpty()) {
                recordNamespace = rootNamespace;
            }
            String recordName = (String) fieldType.get(RECORD_NAME);
            Fqn fqn = new Fqn(recordNamespace, recordName);
            NamesValidator.validateFqnOrThrowException(fqn);
            if (fieldTypeName.equals(FIXED_FIELD_TYPE)) {
                int fixedSize = (int) fieldType.get(FIXED_SIZE);
                fieldSchema = Schema.createFixed(recordName, null, 
                        recordNamespace, fixedSize);
                namedSchemas.put(fqn, fieldSchema);
            } else if (fieldTypeName.equals(ENUM_FIELD_TYPE)) {
                @SuppressWarnings("unchecked")
                GenericData.Array<Record> symbolsArray = (GenericData.Array<Record>) fieldType.get(SYMBOLS);
                List<String> values = new ArrayList<>();
                for (Record symbol : symbolsArray) {
                    String enumSymbol = (String)symbol.get(SYMBOL);
                    NamesValidator.validateEnumSymbolOrThrowException(enumSymbol);
                    values.add(enumSymbol);
                }
                fieldSchema = Schema.createEnum(recordName, null, 
                        recordNamespace, values);
                namedSchemas.put(fqn, fieldSchema);
            } else if (fieldTypeName.equals(RECORD_FIELD_TYPE)) {
                fieldSchema = Schema.createRecord(recordName, null, recordNamespace, false);
                namedSchemas.put(fqn, fieldSchema);
                if (hasCtl) {
                    Integer version = (Integer) fieldType.get(VERSION);
                    if (version != null) {
                        fieldSchema.addProp(VERSION, IntNode.valueOf(version));
                    }
                    @SuppressWarnings("unchecked")
                    GenericData.Array<Record> dependenciesArray = (GenericData.Array<Record>) fieldType.get(DEPENDENCIES);
                    if (dependenciesArray != null) {
                        JsonNodeFactory jsonFactory = JsonNodeFactory.instance;            
                        ArrayNode dependenciesNode = jsonFactory.arrayNode();
                        for (Record dependency : dependenciesArray) {
                            String dependencyFqn = (String) dependency.get(FQN);
                            Integer dependencyVersion = (Integer) dependency.get(VERSION);
                            ObjectNode dependencyNode = jsonFactory.objectNode();
                            dependencyNode.put(FQN, dependencyFqn);
                            dependencyNode.put(VERSION, dependencyVersion);
                            dependenciesNode.add(dependencyNode);
                        }
                        fieldSchema.addProp(DEPENDENCIES, dependenciesNode);
                    }
                }
                String displayName = (String) fieldType.get(DISPLAY_NAME);
                if (displayName != null) {
                    fieldSchema.addProp(DISPLAY_NAME, displayName);
                }
                String description = (String) fieldType.get(DESCRIPTION);
                if (description != null) {
                    fieldSchema.addProp(DESCRIPTION, description);
                }
                @SuppressWarnings("unchecked")
                GenericData.Array<Record> fieldsArray =  (GenericData.Array<Record>)fieldType.get(FIELDS);
                List<Field> recordFields = new ArrayList<Field>();
                if (fieldsArray != null) {
                    for (Record field : fieldsArray) {
                        recordFields.add(createSchemaFieldFromForm(field, namedSchemas, rootNamespace));
                    }
                }
                fieldSchema.setFields(recordFields);
            }
        } else if (fieldTypeName.equals(NAMED_REFERENCE_FIELD_TYPE)) {
            Fqn fqn = new Fqn((String) fieldType.get(FQN));
            fieldSchema = namedSchemas.get(fqn);
            if (fieldSchema == null) {
                throw new IllegalArgumentException("Type with FQN '" + 
                        fqn + "' is not defined in schema.");
            }
        } else if (fieldTypeName.equals(ARRAY_FIELD_TYPE)) {
            Record arrayItem = (Record) fieldType.get(ARRAY_ITEM);            
            Schema elementTypeSchema = createFieldSchema(arrayItem, namedSchemas, rootNamespace);
            fieldSchema = Schema.createArray(elementTypeSchema);
        } else if (fieldTypeName.equals(UNION_FIELD_TYPE)) {
            List<Schema> unionTypes = new ArrayList<>();
            @SuppressWarnings("unchecked")
            GenericData.Array<Record> acceptableValuesArray = 
                    (GenericData.Array<Record>)fieldType.get(ACCEPTABLE_VALUES);
            
            for (Record acceptableValue : acceptableValuesArray) {
                unionTypes.add(createFieldSchema(acceptableValue, namedSchemas, rootNamespace));
            }
            fieldSchema = Schema.createUnion(unionTypes);
        }
        customizeFieldSchema(fieldSchema, fieldType);
        return fieldSchema;
    }
    
    /**
     * Creates the schema field from form.
     *
     * @param field the field
     * @param recordSchemas the record schemas
     * @return the field
     * @throws ParseException the parse exception
     */
    private Field createSchemaFieldFromForm(Record field, Map<Fqn, Schema> recordSchemas, String rootNamespace) throws ParseException {
        Record fieldType = (Record)field.get(FIELD_TYPE);
        Schema fieldSchema = createFieldSchema(fieldType, recordSchemas, rootNamespace);
        Boolean optional = (Boolean)field.get(OPTIONAL);
        if (optional != null && optional && 
                !FormAvroConverter.isNullTypeSchema(fieldSchema)) {            
            if (fieldSchema.getType() == Type.UNION) {
                List<Schema> unionTypes = fieldSchema.getTypes();
                unionTypes = new ArrayList<>(unionTypes);
                unionTypes.add(Schema.create(Type.NULL));                
                fieldSchema = Schema.createUnion(unionTypes);
            } else {
                fieldSchema = Schema.createUnion(Arrays.asList(fieldSchema, Schema.create(Type.NULL)));
            }
        }
        String fieldName = (String)field.get(FIELD_NAME);
        Field avroField = new Field(fieldName, fieldSchema, null, null);
        String displayName = (String)field.get(DISPLAY_NAME);
        if (displayName != null) {
            avroField.addProp(DISPLAY_NAME, displayName);
        }
        String displayPrompt = (String)field.get(DISPLAY_PROMPT);
        if (displayPrompt != null) {
            avroField.addProp(DISPLAY_PROMPT, displayPrompt);
        }
        Float weight = (Float)field.get(WEIGHT);
        if (weight != null) {
            avroField.addProp(WEIGHT, DoubleNode.valueOf(weight));
        }
        Integer keyIndex = (Integer)field.get(KEY_INDEX);
        if (keyIndex != null) {
            avroField.addProp(KEY_INDEX, IntNode.valueOf(keyIndex));
        }
        setDefaultValueFromFieldType(avroField, fieldType);
        
        if (fieldType.getSchema().getName().equals(STRING_FIELD_TYPE)) {
            Integer maxLength = (Integer)fieldType.get(MAX_LENGTH);
            if (maxLength != null) {
                avroField.addProp(MAX_LENGTH, IntNode.valueOf(maxLength));
            }
            GenericData.EnumSymbol inputType = 
                    (GenericData.EnumSymbol)fieldType.get(INPUT_TYPE);
            if (inputType != null) {
                avroField.addProp(INPUT_TYPE, inputType.toString().toLowerCase());
            }
        } else if (fieldType.getSchema().getName().equals(INTEGER_FIELD_TYPE)) {
            Integer maxLength = (Integer)fieldType.get(MAX_LENGTH);
            if (maxLength != null) {
                avroField.addProp(MAX_LENGTH, IntNode.valueOf(maxLength));
            }
        } else if (fieldType.getSchema().getName().equals(LONG_FIELD_TYPE)) {
            Integer maxLength = (Integer)fieldType.get(MAX_LENGTH);
            if (maxLength != null) {
                avroField.addProp(MAX_LENGTH, IntNode.valueOf(maxLength));
            }
        } else if (fieldType.getSchema().getName().equals(FLOAT_FIELD_TYPE)) {
            Integer maxLength = (Integer)fieldType.get(MAX_LENGTH);
            if (maxLength != null) {
                avroField.addProp(MAX_LENGTH, IntNode.valueOf(maxLength));
            }
        } else if (fieldType.getSchema().getName().equals(DOUBLE_FIELD_TYPE)) {
            Integer maxLength = (Integer)fieldType.get(MAX_LENGTH);
            if (maxLength != null) {
                avroField.addProp(MAX_LENGTH, IntNode.valueOf(maxLength));
            }
        } else if (fieldType.getSchema().getName().equals(ENUM_FIELD_TYPE)) {
            @SuppressWarnings("unchecked")
            GenericData.Array<Record> symbolsArray = 
                        (GenericData.Array<Record>) fieldType.get(SYMBOLS);
            JsonNodeFactory jsonFactory = JsonNodeFactory.instance;            
            ArrayNode displayNamesNode = jsonFactory.arrayNode();
            for (Record enumSymbol : symbolsArray) {
                String enumDisplayName = (String)enumSymbol.get("displayName");
                if (enumDisplayName != null) {
                    displayNamesNode.add(enumDisplayName);
                }
            }
            if (displayNamesNode.size() == symbolsArray.size()) {
                avroField.addProp(DISPLAY_NAMES, displayNamesNode);
            }
        } else if (fieldType.getSchema().getName().equals(ARRAY_FIELD_TYPE)) {
            Integer minRowCount = (Integer) fieldType.get(MIN_ROW_COUNT);
            if (minRowCount != null) {
                avroField.addProp(MIN_ROW_COUNT, IntNode.valueOf(minRowCount));
            }
        } 
        customizeSchemaField(avroField, fieldType);
        return avroField;
    }

    /**
     * Sets the default value from field type.
     *
     * @param avroField the avro field
     * @param fieldType the field type
     * @throws ParseException the parse exception
     */
    private static void setDefaultValueFromFieldType(Field avroField, Record fieldType) throws ParseException {
        if (fieldType.getSchema().getName().equals(STRING_FIELD_TYPE)) {
            String defaultValue = (String)fieldType.get(DEFAULT_VALUE);
            if (defaultValue != null) {
                avroField.addProp(BY_DEFAULT, defaultValue);
            }
        } else if (fieldType.getSchema().getName().equals(INTEGER_FIELD_TYPE)) {
            Integer defaultValue = (Integer)fieldType.get(DEFAULT_VALUE);
            if (defaultValue != null) {
                avroField.addProp(BY_DEFAULT, IntNode.valueOf(defaultValue));
            }
        } else if (fieldType.getSchema().getName().equals(LONG_FIELD_TYPE)) {
            Long defaultValue = (Long)fieldType.get(DEFAULT_VALUE);
            if (defaultValue != null) {
                long longVal = defaultValue;
                if (longVal < Integer.MIN_VALUE || longVal > Integer.MAX_VALUE) {
                    avroField.addProp(BY_DEFAULT, LongNode.valueOf(longVal));
                } else {
                    avroField.addProp(BY_DEFAULT, IntNode.valueOf(defaultValue.intValue()));
                }
            }
        } else if (fieldType.getSchema().getName().equals(FLOAT_FIELD_TYPE)) {
            Float defaultValue = (Float)fieldType.get(DEFAULT_VALUE);
            if (defaultValue != null) {
                avroField.addProp(BY_DEFAULT, DoubleNode.valueOf(defaultValue));
            }
        } else if (fieldType.getSchema().getName().equals(DOUBLE_FIELD_TYPE)) {
            Double defaultValue = (Double)fieldType.get(DEFAULT_VALUE);
            if (defaultValue != null) {
                avroField.addProp(BY_DEFAULT, DoubleNode.valueOf(defaultValue));
            }
        } else if (fieldType.getSchema().getName().equals(BOOLEAN_FIELD_TYPE)) {
            Boolean defaultValue = (Boolean)fieldType.get(DEFAULT_VALUE);
            if (defaultValue != null) {
                avroField.addProp(BY_DEFAULT, BooleanNode.valueOf(defaultValue));
            }
        } else if (fieldType.getSchema().getName().equals(BYTES_FIELD_TYPE)) {
            String defaultValue = (String)fieldType.get(DEFAULT_VALUE);
            if (defaultValue != null) {
                avroField.addProp(BY_DEFAULT, createBytesJsonValue(defaultValue));
            }
        } else if (fieldType.getSchema().getName().equals(FIXED_FIELD_TYPE)) {
            String defaultValue = (String)fieldType.get(DEFAULT_VALUE);
            if (defaultValue != null) {
                avroField.addProp(BY_DEFAULT, createBytesJsonValue(defaultValue));
            }
        } else if (fieldType.getSchema().getName().equals(ENUM_FIELD_TYPE)) {
            String defaultValue = (String)fieldType.get(DEFAULT_VALUE);
            if (defaultValue != null) {
                avroField.addProp(BY_DEFAULT, defaultValue);
            }
        } else if (fieldType.getSchema().getName().equals(UNION_FIELD_TYPE)) {
            Record defaultValue = (Record)fieldType.get(DEFAULT_VALUE);
            if (defaultValue != null) {
                JsonNode node = getUnionDefaultValue(defaultValue);
                if (node != null) {
                    avroField.addProp(BY_DEFAULT, node);
                }
            }
        }
    }
    
    /**
     * Creates the bytes json value.
     *
     * @param value the value
     * @return the array node
     * @throws ParseException the parse exception
     */
    private static ArrayNode createBytesJsonValue(String value) throws ParseException {
        if (value != null) {
            JsonNodeFactory jsonFactory = JsonNodeFactory.instance;            
            ArrayNode bytesNode = jsonFactory.arrayNode();
            byte[] data = Base64Utils.fromBase64(value);
            for (int i=0;i<data.length;i++) {
                bytesNode.add(data[i]);
            }
            return bytesNode;
        } else {
            return null;
        }
    }
    
    /**
     * Gets the union default value.
     *
     * @param fieldType the field type
     * @return the union default value
     */
    private static JsonNode getUnionDefaultValue(Record fieldType) {
        if (fieldType.getSchema().getName().equals(STRING_FIELD_TYPE)) {
            String defaultValue = (String)fieldType.get(DEFAULT_VALUE);
            if (defaultValue != null) {
                return TextNode.valueOf(defaultValue);
            }
        } else if (fieldType.getSchema().getName().equals(INTEGER_FIELD_TYPE)) {
            Integer defaultValue = (Integer)fieldType.get(DEFAULT_VALUE);
            if (defaultValue != null) {
                return IntNode.valueOf(defaultValue);
            }
        } else if (fieldType.getSchema().getName().equals(LONG_FIELD_TYPE)) {
            Long defaultValue = (Long)fieldType.get(DEFAULT_VALUE);
            if (defaultValue != null) {
                long longVal = defaultValue;
                if (longVal < Integer.MIN_VALUE || longVal > Integer.MAX_VALUE) {
                    return LongNode.valueOf(longVal);
                } else {
                    return IntNode.valueOf(defaultValue.intValue());
                }
            }
        } else if (fieldType.getSchema().getName().equals(FLOAT_FIELD_TYPE)) {
            Float defaultValue = (Float)fieldType.get(DEFAULT_VALUE);
            if (defaultValue != null) {
                return DoubleNode.valueOf(defaultValue);
            }
        } else if (fieldType.getSchema().getName().equals(DOUBLE_FIELD_TYPE)) {
            Double defaultValue = (Double)fieldType.get(DEFAULT_VALUE);
            if (defaultValue != null) {
                return DoubleNode.valueOf(defaultValue);
            }
        } else if (fieldType.getSchema().getName().equals(BOOLEAN_FIELD_TYPE)) {
            Boolean defaultValue = (Boolean)fieldType.get(DEFAULT_VALUE);
            if (defaultValue != null) {
                return BooleanNode.valueOf(defaultValue);
            }
        } else if (fieldType.getSchema().getName().equals(BYTES_FIELD_TYPE)) {
            String defaultValue = (String)fieldType.get(DEFAULT_VALUE);
            if (defaultValue != null) {
                return TextNode.valueOf(defaultValue);
            }
        } else if (fieldType.getSchema().getName().equals(FIXED_FIELD_TYPE)) {
            String defaultValue = (String)fieldType.get(DEFAULT_VALUE);
            if (defaultValue != null) {
                return TextNode.valueOf(defaultValue);
            }
        } else if (fieldType.getSchema().getName().equals(ENUM_FIELD_TYPE)) {
            String defaultValue = (String)fieldType.get(DEFAULT_VALUE);
            if (defaultValue != null) {
                return TextNode.valueOf(defaultValue);
            }
        }
        return null;
    }
    
}

