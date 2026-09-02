package fr.permsmp.afkgodnode;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SecretStore {
    private static final String ALIAS="afk_god_node_secret_v1";
    private static SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(!ks.containsAlias(ALIAS)){
            KeyGenerator kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            kg.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            return kg.generateKey();
        }
        return (SecretKey)ks.getKey(ALIAS,null);
    }
    static String encrypt(String plain)throws Exception{
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());byte[]iv=c.getIV(),ct=c.doFinal(plain.getBytes(StandardCharsets.UTF_8));byte[]all=new byte[1+iv.length+ct.length];all[0]=(byte)iv.length;System.arraycopy(iv,0,all,1,iv.length);System.arraycopy(ct,0,all,1+iv.length,ct.length);return Base64.encodeToString(all,Base64.NO_WRAP);
    }
    static String decrypt(String encoded)throws Exception{
        byte[]all=Base64.decode(encoded,Base64.NO_WRAP);int n=all[0]&0xff;byte[]iv=new byte[n],ct=new byte[all.length-1-n];System.arraycopy(all,1,iv,0,n);System.arraycopy(all,1+n,ct,0,ct.length);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));return new String(c.doFinal(ct),StandardCharsets.UTF_8);
    }
}
