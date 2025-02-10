package net.engineeringdigest.journalApp.api;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QuoteResponse {
    private Data data;
    @Getter
    @Setter
    public class Data{
        public String author;
        public String quote;
    }

}
