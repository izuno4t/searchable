package io.searchable.example.api.security;

import io.searchable.example.api.config.SearchableProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ApiKeyFilterTest {

    private static SearchableProperties propsWithKey(final String key) {
        final SearchableProperties props = new SearchableProperties();
        props.getApi().setKey(key);
        return props;
    }

    private static MockHttpServletRequest get(final String header) {
        final MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/health");
        if (header != null) {
            req.addHeader(ApiKeyFilter.HEADER, header);
        }
        return req;
    }

    @Test
    void disabledFilterPassesEveryRequest() throws Exception {
        final ApiKeyFilter filter = new ApiKeyFilter(propsWithKey(null));
        final FilterChain chain = mock(FilterChain.class);
        final MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(get(null), res, chain);

        verify(chain, times(1)).doFilter(any(), any());
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void enabledFilterRejectsRequestWithoutHeader() throws Exception {
        final ApiKeyFilter filter = new ApiKeyFilter(propsWithKey("secret-key-42"));
        final FilterChain chain = mock(FilterChain.class);
        final MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(get(null), res, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("INVALID_API_KEY");
    }

    @Test
    void enabledFilterRejectsRequestWithWrongHeader() throws Exception {
        final ApiKeyFilter filter = new ApiKeyFilter(propsWithKey("secret-key-42"));
        final FilterChain chain = mock(FilterChain.class);
        final MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(get("not-the-key"), res, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test
    void enabledFilterRejectsHeaderThatIsAPrefixOfTheConfiguredKey() throws Exception {
        // Length-difference must not leak via constantTimeEquals: a strict
        // prefix is not equal even though MessageDigest.isEqual on the
        // padded buffers would compare byte-for-byte.
        final ApiKeyFilter filter = new ApiKeyFilter(propsWithKey("secret-key-42"));
        final FilterChain chain = new MockFilterChain();
        final MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(get("secret-key-4"), res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test
    void enabledFilterAcceptsMatchingHeader() throws Exception {
        final ApiKeyFilter filter = new ApiKeyFilter(propsWithKey("secret-key-42"));
        final FilterChain chain = mock(FilterChain.class);
        final MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(get("secret-key-42"), res, chain);

        verify(chain, times(1)).doFilter(any(), any());
        assertThat(res.getStatus()).isEqualTo(200);
    }
}
