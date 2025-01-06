package org.moqui.ollama

import io.github.ollama4j.types.OllamaModelType;
import tech.amikos.chromadb.Client;
import tech.amikos.chromadb.Collection;
import tech.amikos.chromadb.embeddings.EmbeddingFunction;
import tech.amikos.chromadb.embeddings.WithParam;
import tech.amikos.chromadb.embeddings.ollama.OllamaEmbeddingFunction;
import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.models.chat.OllamaChatRequest;
import io.github.ollama4j.models.chat.OllamaChatRequestBuilder;
import io.github.ollama4j.models.chat.OllamaChatResult;
import io.github.ollama4j.models.chat.OllamaChatMessageRole;

import java.util.Arrays;

public class StockAnalysisWithKnowledgeQuery {

    public static void main(String[] args) {
        analyzeStockWithKnowledge();
    }

    public static void analyzeStockWithKnowledge() {
        try {
            // 初始化 ChromaDB 客户端
            System.setProperty("CHROMA_URL", "http://127.0.0.1:8000");
            Client client = new Client("http://127.0.0.1:8000");
            // 配置 Ollama Embedding Function
            System.setProperty("OLLAMA_URL", "http://localhost:11434/api/embed");
            EmbeddingFunction ef = new OllamaEmbeddingFunction(WithParam.baseAPI(System.getProperty("OLLAMA_URL")));
            Collection collection = client.getCollection("chanlun-knowledge", ef);

            // 查询缠论相关内容
            String query = ("请根据以下缠论规则来判断楚天龙股票的三个月日K线数据中的趋势：\n" +
                    "1. 判定是否处于上升趋势、下降趋势或震荡区间。\n" +
                    "2. 判断是否符合缠论中的买入信号，考虑到是否出现背离等情况。\n" +
                    "3. 判断是否有卖出信号，考虑到缠论中的顶背离等因素。\n");
            Collection.QueryResponse qr = collection.query(Arrays.asList(query), 5, null, null, null);

            StringBuilder retrievedKnowledge = new StringBuilder();
            if (qr.getDocuments() != null) {
                for (String doc : qr.getDocuments()) {
                    retrievedKnowledge.append(doc).append("\n");
                }
            } else {
                System.out.println("未检索到相关文档。");
            }

            // 股票数据（过去半年日 K 数据）
            String kLineData = """
                003040 楚天龙 日线 不复权
                      日期\t    开盘\t    最高\t    最低\t    收盘\t    成交量\t    成交额
                2024/07/01\t12.24\t12.51\t11.96\t12.34\t12348456\t150866304.00
                2024/07/02\t12.26\t12.87\t12.21\t12.65\t18917610\t239634576.00
                2024/07/03\t12.47\t12.55\t11.99\t12.02\t14029507\t171098112.00
                2024/07/04\t11.80\t12.00\t11.38\t11.41\t10289535\t119579424.00
                2024/07/05\t11.38\t11.69\t11.13\t11.59\t6899383\t79350024.00
                2024/07/08\t11.59\t11.59\t11.00\t11.02\t7533249\t84236712.00
                2024/07/09\t11.02\t11.27\t10.68\t11.22\t7978742\t87823784.00
                2024/07/10\t11.21\t11.35\t11.01\t11.02\t5007700\t55804368.00
                2024/07/11\t11.02\t11.24\t10.99\t11.17\t7427200\t82632360.00
                2024/07/12\t11.08\t11.23\t11.02\t11.03\t4815000\t53464824.00
                2024/07/15\t11.00\t11.04\t10.56\t10.64\t7435800\t79687688.00
                2024/07/16\t10.60\t10.72\t10.48\t10.66\t4342200\t46065080.00
                2024/07/17\t10.66\t10.75\t10.50\t10.51\t4223925\t44629432.00
                2024/07/18\t10.48\t10.48\t10.09\t10.24\t5673900\t57875576.00
                2024/07/19\t10.13\t10.62\t10.11\t10.55\t5827604\t60878436.00
                2024/07/22\t10.59\t10.77\t10.55\t10.70\t4957500\t52986204.00
                2024/07/23\t10.61\t10.79\t10.52\t10.54\t4849887\t51845164.00
                2024/07/24\t10.51\t10.59\t10.23\t10.26\t4504181\t46715504.00
                2024/07/25\t10.62\t10.86\t10.32\t10.66\t12151911\t128380592.00
                2024/07/26\t10.40\t10.63\t10.36\t10.55\t8107987\t85283264.00
                2024/07/29\t10.60\t10.82\t10.57\t10.67\t6656822\t71184456.00
                2024/07/30\t10.59\t10.83\t10.58\t10.77\t6498898\t69788056.00
                2024/07/31\t10.74\t11.17\t10.69\t11.11\t8418333\t92662040.00
                2024/08/01\t11.22\t11.22\t11.06\t11.10\t6338600\t70572032.00
                2024/08/02\t11.11\t11.20\t10.85\t10.90\t5442700\t60035328.00
                2024/08/05\t10.91\t10.97\t10.42\t10.43\t5658100\t60449884.00
                2024/08/06\t10.50\t10.69\t10.42\t10.55\t3894688\t40994100.00
                2024/08/07\t10.51\t10.77\t10.51\t10.68\t4365000\t46561800.00
                2024/08/08\t10.61\t10.73\t10.43\t10.63\t3873200\t41033260.00
                2024/08/09\t10.79\t10.82\t10.47\t10.47\t3375587\t35809892.00
                2024/08/12\t10.35\t10.48\t10.21\t10.26\t3626267\t37328528.00
                2024/08/13\t10.25\t10.38\t10.15\t10.28\t3303900\t33814040.00
                2024/08/14\t10.28\t10.41\t10.24\t10.34\t3007100\t31109404.00
                2024/08/15\t10.31\t10.61\t10.23\t10.50\t4145100\t43429912.00
                2024/08/16\t10.52\t10.57\t10.41\t10.42\t2674400\t28071672.00
                2024/08/19\t10.50\t10.88\t10.43\t10.60\t8427200\t90048128.00
                2024/08/20\t10.50\t10.54\t10.21\t10.24\t5989460\t61844584.00
                2024/08/21\t10.18\t10.40\t10.15\t10.18\t3553000\t36486680.00
                2024/08/22\t10.18\t10.29\t9.81\t9.81\t5618270\t56063032.00
                2024/08/23\t9.81\t10.02\t9.66\t9.92\t4903067\t48321560.00
                2024/08/26\t9.92\t10.00\t9.78\t9.86\t2834967\t28045462.00
                2024/08/27\t9.85\t9.86\t9.58\t9.62\t3526900\t34101224.00
                2024/08/28\t9.52\t9.55\t9.13\t9.38\t5176232\t48403908.00
                2024/08/29\t9.25\t9.67\t9.25\t9.58\t4331000\t41243960.00
                2024/08/30\t9.62\t10.07\t9.57\t9.95\t6122300\t60724376.00
                2024/09/02\t9.95\t10.00\t9.63\t9.64\t4274100\t41833344.00
                2024/09/03\t9.66\t9.81\t9.59\t9.74\t3190200\t30957064.00
                2024/09/04\t9.65\t9.78\t9.62\t9.67\t2310800\t22378036.00
                2024/09/05\t10.08\t10.50\t9.94\t10.01\t12017446\t121064048.00
                2024/09/06\t10.01\t10.20\t9.67\t9.70\t9687500\t96731208.00
                2024/09/09\t9.51\t9.83\t9.46\t9.69\t5590469\t54035524.00
                2024/09/10\t9.69\t9.82\t9.48\t9.78\t5178000\t49926772.00
                2024/09/11\t9.70\t9.75\t9.60\t9.64\t2941433\t28404640.00
                2024/09/12\t9.65\t9.81\t9.65\t9.70\t3783600\t36869032.00
                2024/09/13\t9.71\t9.73\t9.42\t9.45\t3514900\t33550818.00
                2024/09/18\t9.35\t9.48\t9.15\t9.26\t3223466\t29910340.00
                2024/09/19\t9.32\t9.78\t9.32\t9.75\t7111834\t68434688.00
                2024/09/20\t9.71\t10.02\t9.67\t9.84\t5457961\t53857084.00
                2024/09/23\t9.90\t9.98\t9.75\t9.91\t3833600\t37970448.00
                2024/09/24\t9.92\t10.18\t9.80\t10.18\t6549600\t65815288.00
                2024/09/25\t10.28\t10.77\t10.22\t10.46\t11259577\t118348448.00
                2024/09/26\t10.46\t10.68\t10.32\t10.68\t7888100\t82987328.00
                2024/09/27\t10.85\t11.38\t10.73\t11.20\t11724100\t129319488.00
                2024/09/30\t11.70\t12.32\t11.45\t12.32\t19996685\t240527504.00
            """;

            // 配置 Ollama API 的主机地址
            String host = "http://localhost:11434/";

            // 初始化 Ollama API 客户端
            OllamaAPI ollamaAPI = new OllamaAPI(host);
            ollamaAPI.setRequestTimeoutSeconds(600); // 设置请求超时时间

            // 构建聊天请求（含系统提示和用户消息）
            OllamaChatRequestBuilder builder = OllamaChatRequestBuilder.getInstance("0xroyce/plutus");
            OllamaChatRequest requestModel = builder
                    .withMessage(OllamaChatMessageRole.SYSTEM, "你是一个使用缠论进行股票分析的智能分析师。")
                    .withMessage(OllamaChatMessageRole.USER,
                            "以下是缠论的技术分析知识和日 K 数据，请分析这只股票的走势及买卖点：\n\n" +
                                    "缠论知识：\n" + retrievedKnowledge.toString() + "\n\n" +
                                    "股票日 K 数据：\n" + kLineData)
                    .build();

            // 调用 chat 方法获取分析结果
            OllamaChatResult chatResult = ollamaAPI.chat(requestModel);
            System.out.println("分析结果：\n" + chatResult.getResponse());

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("股票分析时发生错误！");
        }
    }
}
