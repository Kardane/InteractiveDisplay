package com.interactivedisplay.core.layout;

import com.interactivedisplay.core.window.WindowDefinition;
import java.util.List;

public interface LayoutEngine {
    List<LayoutComponent> calculate(WindowDefinition definition);
}
