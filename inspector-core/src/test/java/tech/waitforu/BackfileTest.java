package tech.waitforu;

/**
 * ClassName: BackfileTest
 * Package: tech.waitforu
 * Description:
 * Author: LiuKe
 * Create: 2026/6/13 17:36
 * Version 1.0
 */
public class BackfileTest {
    public static void main(String[] args) {
        BackFile backFile = new BackFile("/Users/liuke/IdeaProjects/NachiNetInspector/inspector-core/src/test/测试/APR15R1");

        NachiNetResume netResume = NachiInspector.inspect(backFile);
        System.out.println(netResume.toString());

        System.out.println("0x%02X".formatted(27));
    }

}
