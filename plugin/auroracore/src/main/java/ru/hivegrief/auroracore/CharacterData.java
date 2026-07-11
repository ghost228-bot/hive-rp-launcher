package ru.hivegrief.auroracore;

public class CharacterData {
    public String nick;
    public String firstName;
    public String lastName;
    public String gender; // male / female
    public int age;
    public String skin;   // ник-источник скина
    public long created;

    public String rpName() {
        return firstName + " " + lastName;
    }
}
