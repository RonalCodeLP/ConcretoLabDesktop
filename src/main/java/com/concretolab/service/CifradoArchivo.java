package com.concretolab.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class CifradoArchivo {
    private static final byte[] ERICK_DELGADO_MAGIA = "CLCIF1".getBytes(StandardCharsets.US_ASCII);
    private static final int ERICK_DELGADO_TAM_SAL = 16;
    private static final int ERICK_DELGADO_TAM_IV = 12;
    private static final int ERICK_DELGADO_TAM_ETIQUETA = 16;
    private static final int ERICK_DELGADO_ITERACIONES = 210_000;
    private static final int ERICK_DELGADO_BITS_CLAVE = 256;
    private static final int ERICK_DELGADO_CABECERA = 6 + ERICK_DELGADO_TAM_SAL + ERICK_DELGADO_TAM_IV;
    private static final String ERICK_DELGADO_MENSAJE_ERROR = "Contraseña incorrecta o archivo dañado.";
    private static final SecureRandom ERICK_DELGADO_ALEATORIO = new SecureRandom();

    private CifradoArchivo() {
    }

    public static byte[] erickDelgadoCifrar(byte[] datos, char[] contrasena) {
        if (datos == null) throw new IllegalArgumentException("No hay datos para cifrar.");
        erickDelgadoValidarContrasena(contrasena);
        byte[] sal = new byte[ERICK_DELGADO_TAM_SAL];
        byte[] iv = new byte[ERICK_DELGADO_TAM_IV];
        ERICK_DELGADO_ALEATORIO.nextBytes(sal);
        ERICK_DELGADO_ALEATORIO.nextBytes(iv);
        byte[] clave = erickDelgadoDerivar(contrasena, sal);
        try {
            byte[] cabecera = erickDelgadoCabecera(sal, iv);
            Cipher cifrador = Cipher.getInstance("AES/GCM/NoPadding");
            cifrador.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(clave, "AES"), new GCMParameterSpec(ERICK_DELGADO_TAM_ETIQUETA * 8, iv));
            cifrador.updateAAD(cabecera);
            byte[] cuerpo = cifrador.doFinal(datos);
            byte[] salida = new byte[cabecera.length + cuerpo.length];
            System.arraycopy(cabecera, 0, salida, 0, cabecera.length);
            System.arraycopy(cuerpo, 0, salida, cabecera.length, cuerpo.length);
            return salida;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar el archivo.");
        } finally {
            Arrays.fill(clave, (byte) 0);
        }
    }

    public static byte[] erickDelgadoDescifrar(byte[] cifrado, char[] contrasena) {
        if (cifrado == null || contrasena == null || contrasena.length == 0
                || cifrado.length < ERICK_DELGADO_CABECERA + ERICK_DELGADO_TAM_ETIQUETA || !erickDelgadoEstaCifrado(cifrado)) {
            throw new IllegalArgumentException(ERICK_DELGADO_MENSAJE_ERROR);
        }
        byte[] sal = Arrays.copyOfRange(cifrado, ERICK_DELGADO_MAGIA.length, ERICK_DELGADO_MAGIA.length + ERICK_DELGADO_TAM_SAL);
        byte[] iv = Arrays.copyOfRange(cifrado, ERICK_DELGADO_MAGIA.length + ERICK_DELGADO_TAM_SAL, ERICK_DELGADO_CABECERA);
        byte[] clave = erickDelgadoDerivar(contrasena, sal);
        try {
            Cipher cifrador = Cipher.getInstance("AES/GCM/NoPadding");
            cifrador.init(Cipher.DECRYPT_MODE, new SecretKeySpec(clave, "AES"), new GCMParameterSpec(ERICK_DELGADO_TAM_ETIQUETA * 8, iv));
            cifrador.updateAAD(cifrado, 0, ERICK_DELGADO_CABECERA);
            return cifrador.doFinal(cifrado, ERICK_DELGADO_CABECERA, cifrado.length - ERICK_DELGADO_CABECERA);
        } catch (GeneralSecurityException | RuntimeException e) {
            throw new IllegalArgumentException(ERICK_DELGADO_MENSAJE_ERROR);
        } finally {
            Arrays.fill(clave, (byte) 0);
        }
    }

    public static boolean erickDelgadoEstaCifrado(byte[] inicio) {
        if (inicio == null || inicio.length < ERICK_DELGADO_MAGIA.length) return false;
        for (int i = 0; i < ERICK_DELGADO_MAGIA.length; i++) {
            if (inicio[i] != ERICK_DELGADO_MAGIA[i]) return false;
        }
        return true;
    }

    private static void erickDelgadoValidarContrasena(char[] contrasena) {
        if (contrasena == null || contrasena.length == 0) throw new IllegalArgumentException("Ingrese una contraseña.");
    }

    private static byte[] erickDelgadoCabecera(byte[] sal, byte[] iv) {
        byte[] c = new byte[ERICK_DELGADO_CABECERA];
        System.arraycopy(ERICK_DELGADO_MAGIA, 0, c, 0, ERICK_DELGADO_MAGIA.length);
        System.arraycopy(sal, 0, c, ERICK_DELGADO_MAGIA.length, ERICK_DELGADO_TAM_SAL);
        System.arraycopy(iv, 0, c, ERICK_DELGADO_MAGIA.length + ERICK_DELGADO_TAM_SAL, ERICK_DELGADO_TAM_IV);
        return c;
    }

    private static byte[] erickDelgadoDerivar(char[] contrasena, byte[] sal) {
        PBEKeySpec especificacion = new PBEKeySpec(contrasena, sal, ERICK_DELGADO_ITERACIONES, ERICK_DELGADO_BITS_CLAVE);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(especificacion).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo preparar el cifrado.");
        } finally {
            especificacion.clearPassword();
        }
    }
}
