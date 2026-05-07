package com.cisternino.infrastructure.ai;

import com.cisternino.domain.entities.*;
import org.springframework.stereotype.Component;

@Component
public class AiDefaultsAdapter implements AiDefaults {
    private final AiProps props;
    public AiDefaultsAdapter(AiProps props) { this.props = props; }

    @Override public AiProviderKey defaultProvider() { return props.getDefaultProvider(); }
    @Override public OpenAiModel defaultOpenAiModel() { return props.getOpenai().getDefaultModel(); }
}