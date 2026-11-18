package xyz.volcanobay.empiric.data;

import xyz.volcanobay.empiric.api.data.IdOrTag;
import xyz.volcanobay.empiric.api.data.IdSet;
import xyz.volcanobay.empiric.core.data.DataBuffer;
import xyz.volcanobay.empiric.core.data.DataContext;
import xyz.volcanobay.empiric.core.data.DataException;
import xyz.volcanobay.empiric.core.data.Spec;
import xyz.volcanobay.empiric.core.data.SpecRegistry;
import xyz.volcanobay.empiric.core.data.tree.DataNode;
import xyz.volcanobay.empiric.core.registry.Register;

import java.util.ArrayList;
import java.util.List;

public final class IdSpecs {

    @Register
    public static final Spec<IdOrTag<?>> ID_OR_TAG = new IdOrTagSpec();
    @Register
    public static final Spec<IdSet<?>> ID_SET = new IdSetSpec();

    private IdSpecs() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register(SpecRegistry registry) {
        registry.register((Class) IdOrTag.class, ID_OR_TAG);
        registry.register((Class) IdSet.class, ID_SET);
    }

    private static IdOrTag<Object> parse(String text) {
        IdOrTag<Object> parsed = IdOrTag.tryParse(text);
        if (parsed == null) {
            throw new DataException("\"" + text + "\" is not a valid id or #tag");
        }
        return parsed;
    }

    private static final class IdOrTagSpec implements Spec<IdOrTag<?>> {

        @Override
        public void write(DataBuffer buffer, IdOrTag<?> value, DataContext context) {
            buffer.putString(value.toString());
        }

        @Override
        public IdOrTag<?> read(DataBuffer buffer, DataContext context) {
            return parse(buffer.takeString());
        }
    }

    private static final class IdSetSpec implements Spec<IdSet<?>> {

        @Override
        public void write(DataBuffer buffer, IdSet<?> value, DataContext context) {
            List<String> texts = new ArrayList<>();
            if (value.isAny()) {
                texts.add("*");
            } else {
                for (IdOrTag<?> element : value.elements()) {
                    texts.add(element.toString());
                }
            }
            if (!buffer.structured()) {
                buffer.putVarInt(texts.size());
                for (String text : texts) {
                    buffer.putString(text);
                }
                return;
            }
            if (texts.size() == 1) {
                buffer.putNode(DataNode.of(texts.get(0)));
                return;
            }
            DataNode array = DataNode.array();
            for (String text : texts) {
                array.add(DataNode.of(text));
            }
            buffer.putNode(array);
        }

        @Override
        public IdSet<?> read(DataBuffer buffer, DataContext context) {
            List<String> texts = new ArrayList<>();
            if (buffer.structured()) {
                DataNode node = buffer.takeNode();
                if (!node.isNull()) {
                    for (DataNode element : node.elementsOrSelf()) {
                        texts.add(element.asString());
                    }
                }
            } else {
                int count = buffer.takeVarInt();
                for (int i = 0; i < count; i++) {
                    texts.add(buffer.takeString());
                }
            }
            List<IdOrTag<Object>> parsed = new ArrayList<>();
            for (String text : texts) {
                if (text.trim().equals("*")) {
                    return IdSet.any();
                }
                parsed.add(parse(text));
            }
            return IdSet.of(parsed);
        }
    }
}
