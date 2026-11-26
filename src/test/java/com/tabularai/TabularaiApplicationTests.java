package com.tabularai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.tabularai.repository.AiReportRepository;
import com.tabularai.repository.ColumnProfileRepository;
import com.tabularai.repository.DatasetRepository;

@SpringBootTest
@AutoConfigureMockMvc
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
})
class TabularaiApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DatasetRepository datasetRepository;

    @MockitoBean
    private ColumnProfileRepository columnProfileRepository;

    @MockitoBean
    private AiReportRepository aiReportRepository;

    @Test
    void shouldGenerateOpenApiDocs() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andDo(print())
                .andExpect(status().isOk());
    }
}
