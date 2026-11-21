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

/**
 * Cifrado de archivos con contraseña: AES-256-GCM con clave derivada por PBKDF2WithHmacSHA256.
 * Formato: "CLCIF1" (ASCII) + sal (16) + IV (12) + texto cifrado con etiqueta de autenticación.
 * La cabecera completa se autentica como datos adicionales, así que alterarla también se detecta.
 */
public final class CifradoArchivo {

    private static final byte[] MAGIA = "CLCIF1".getBytes(StandardCharsets.US_ASCII);
    private static final int TAM_SAL = 16;
    private static final int TAM_IV = 12;
    private static final int TAM_ETIQUETA = 16;
    private static final int ITERACIONES = 210_000;
    private static final int BITS_CLAVE = 256;
    private static final int CABECERA = 6 + TAM_SAL + TAM_IV;
    private static final String MENSAJE_ERROR = "Contraseña incorrecta o archivo dañado.";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private CifradoArchivo() {
    }

    public static byte[] cifrar(byte[] datos, char[] contrasena) {
        if (datos == null) throw new IllegalArgumentException("No hay datos para cifrar.");
        validarContrasena(contrasena);
        byte[] sal = new byte[TAM_SAL];
        byte[] iv = new byte[TAM_IV];
        ALEATORIO.nextBytes(sal);
        ALEATORIO.nextBytes(iv);
        byte[] clave = derivar(contrasena, sal);
        try {
            byte[] cabecera = cabecera(sal, iv);
            Cipher cifrador = Cipher.getInstance("AES/GCM/NoPadding");
            cifrador.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(clave, "AES"), new GCMParameterSpec(TAM_ETIQUETA * 8, iv));
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

    public static byte[] descifrar(byte[] cifrado, char[] contrasena) {
        if (cifrado == null || contrasena == null || contrasena.length == 0
                || cifrado.length < CABECERA + TAM_ETIQUETA || !estaCifrado(cifrado)) {
            throw new IllegalArgumentException(MENSAJE_ERROR);
        }
        byte[] sal = Arrays.copyOfRange(cifrado, MAGIA.length, MAGIA.length + TAM_SAL);
        byte[] iv = Arrays.copyOfRange(cifrado, MAGIA.length + TAM_SAL, CABECERA);
        byte[] clave = derivar(contrasena, sal);
        try {
            Cipher cifrador = Cipher.getInstance("AES/GCM/NoPadding");
            cifrador.init(Cipher.DECRYPT_MODE, new SecretKeySpec(clave, "AES"), new GCMParameterSpec(TAM_ETIQUETA * 8, iv));
            cifrador.updateAAD(cifrado, 0, CABECERA);
            return cifrador.doFinal(cifrado, CABECERA, cifrado.length - CABECERA);
        } catch (GeneralSecurityException | RuntimeException e) {
            throw new IllegalArgumentException(MENSAJE_ERROR);
        } finally {
            Arrays.fill(clave, (byte) 0);
        }
    }

    /** Indica si los primeros bytes corresponden a un archivo cifrado por esta utilidad. */
    public static boolean estaCifrado(byte[] inicio) {
        if (inicio == null || inicio.length < MAGIA.length) return false;
        for (int i = 0; i < MAGIA.length; i++) {
            if (inicio[i] != MAGIA[i]) return false;
        }
        return true;
    }

    private static void validarContrasena(char[] contrasena) {
        if (contrasena == null || contrasena.length == 0) throw new IllegalArgumentException("Ingrese una contraseña.");
    }

    private static byte[] cabecera(byte[] sal, byte[] iv) {
        byte[] c = new byte[CABECERA];
        System.arraycopy(MAGIA, 0, c, 0, MAGIA.length);
        System.arraycopy(sal, 0, c, MAGIA.length, TAM_SAL);
        System.arraycopy(iv, 0, c, MAGIA.length + TAM_SAL, TAM_IV);
        return c;
    }

    private static byte[] derivar(char[] contrasena, byte[] sal) {
        PBEKeySpec especificacion = new PBEKeySpec(contrasena, sal, ITERACIONES, BITS_CLAVE);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(especificacion).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo preparar el cifrado.");
        } finally {
            especificacion.clearPassword();
        }
    }
}
