package io.github.druo7.journal.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Rejects request bodies over 512 KB before they are parsed (JSON bodies are not covered by
 * Tomcat's form-size limits). Enforced on Content-Length and, for chunked bodies, while reading.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestSizeLimitFilter extends OncePerRequestFilter {
    static final long MAX_BYTES = 512 * 1024;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > MAX_BYTES) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }
        chain.doFilter(new LimitedRequest(request), response);
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {
        LimitedRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            ServletInputStream in = super.getInputStream();
            return new ServletInputStream() {
                private long read;

                @Override
                public int read() throws IOException {
                    int b = in.read();
                    if (b >= 0 && ++read > MAX_BYTES) {
                        throw new IOException("Request body too large");
                    }
                    return b;
                }

                @Override
                public int read(byte[] buf, int off, int len) throws IOException {
                    int n = in.read(buf, off, len);
                    if (n > 0 && (read += n) > MAX_BYTES) {
                        throw new IOException("Request body too large");
                    }
                    return n;
                }

                @Override
                public boolean isFinished() {
                    return in.isFinished();
                }

                @Override
                public boolean isReady() {
                    return in.isReady();
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    in.setReadListener(listener);
                }
            };
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String enc = getCharacterEncoding();
            return new BufferedReader(new InputStreamReader(getInputStream(), enc == null ? StandardCharsets.UTF_8.name() : enc));
        }
    }
}
