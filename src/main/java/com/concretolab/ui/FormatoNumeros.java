package com.concretolab.ui;
import java.math.BigDecimal;
import java.math.RoundingMode;
final class FormatoNumeros {
 private FormatoNumeros(){}
 static String yefriLermaVolumen(double valor){return BigDecimal.valueOf(valor).setScale(6,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.',',');}
}
