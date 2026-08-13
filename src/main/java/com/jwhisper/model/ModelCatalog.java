package com.jwhisper.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ModelCatalog {
    private static final String TINY_EN_REVISION = "3a6d57ee9c665610614068e8592d8baee0188181";
    private static final String BASE_EN_REVISION = "51eefc0af78b103839eda9e7e4f4186acc6517fe";
    private static final String SMALL_EN_REVISION = "e90fd4d427f745ae9beec2560f26dbd71aaa10f2";
    private static final String MEDIUM_REVISION = "d3978248a6b5de6df7ec29ddfbde3993845fa806";
    private static final String LARGE_V3_REVISION = "3b6257ad5e67aa523c7c07f4fea04d445eecc4a6";

    private static final List<ModelDescriptor> AVAILABLE = List.of(
            descriptor(
                    "tiny.en",
                    "tiny.en",
                    "onnx-community/whisper-tiny.en",
                    TINY_EN_REVISION,
                    "Fast English model",
                    285_000_000L,
                    "8c361b9430a5ef6619ee64b7fe06c725df19f36d508cc8b847064b34a888a3fe",
                    "14f1d425a4821feeba77cf93eeeaf812ca816f2e3fec382b4f0fa93d29de710e",
                    new ModelFile(
                            "onnx/decoder_with_past_model.onnx",
                            113_637_462L,
                            "0ed76a8f8b9448c9eb74ad07549b65285d29dd36f4e42911fc65d67becbe9458"
                    )
            ),
            descriptor(
                    "base.en",
                    "base.en",
                    "onnx-community/whisper-base.en",
                    BASE_EN_REVISION,
                    "Balanced English model",
                    294_000_000L,
                    "1cc86302d480b061452d348638064383ab41b6f3333ddd0e423532d14edaf535",
                    null,
                    null
            ),
            descriptor(
                    "small.en",
                    "small.en",
                    "onnx-community/whisper-small.en",
                    SMALL_EN_REVISION,
                    "More accurate English model",
                    970_000_000L,
                    null,
                    null,
                    null
            ),
            descriptor(
                    "medium",
                    "medium",
                    "onnx-community/whisper-medium-ONNX",
                    MEDIUM_REVISION,
                    "Large multilingual model",
                    3_200_000_000L,
                    null,
                    null,
                    null
            ),
            descriptor(
                    "large-v3",
                    "large-v3",
                    "onnx-community/whisper-large-v3-ONNX",
                    LARGE_V3_REVISION,
                    "Most accurate multilingual model",
                    6_500_000_000L,
                    null,
                    null,
                    null
            )
    );

    private ModelCatalog() {
    }

    public static List<ModelDescriptor> availableModels() {
        return AVAILABLE;
    }

    public static Optional<ModelDescriptor> find(String id) {
        return AVAILABLE.stream().filter(model -> model.id().equals(id)).findFirst();
    }

    private static ModelDescriptor descriptor(
            String id,
            String displayName,
            String repository,
            String revision,
            String description,
            long estimatedBytes,
            String encoderSha256,
            String decoderSha256,
            ModelFile decoderWithPast
    ) {
        return new ModelDescriptor(
                id,
                displayName,
                repository,
                revision,
                description,
                estimatedBytes,
                standardFiles(encoderSha256, decoderSha256, decoderWithPast)
        );
    }

    private static List<ModelFile> standardFiles(
            String encoderSha256,
            String decoderSha256,
            ModelFile decoderWithPast
    ) {
        List<ModelFile> files = new ArrayList<>();
        files.add(new ModelFile("config.json", 0, null));
        files.add(new ModelFile("generation_config.json", 0, null));
        files.add(new ModelFile("preprocessor_config.json", 0, null));
        files.add(new ModelFile("tokenizer_config.json", 0, null));
        files.add(new ModelFile("tokenizer.json", 0, null));
        files.add(new ModelFile("onnx/encoder_model.onnx", 0, encoderSha256));
        files.add(new ModelFile("onnx/decoder_model.onnx", 0, decoderSha256));
        if (decoderWithPast != null) {
            files.add(decoderWithPast);
        }
        return List.copyOf(files);
    }
}