package com.cielo.flashbooking.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;

class RequestPayloadLimitFilterTest {

    @Test
    void doFilter_whenLengthIsUnknownAndBodyExceedsLimit_returnsPayloadTooLarge() throws Exception {
        RequestPayloadLimitFilter filter = new RequestPayloadLimitFilter(DataSize.ofBytes(4));
        MockHttpServletRequest request = new MockHttpServletRequest() {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContent("12345".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(413);
    }
}
