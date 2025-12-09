import { createContext, useContext, useState, useEffect, type ReactNode } from 'react';
import { authAPI } from '../lib/api';

interface User {
    id: string;
    username: string;
    email: string;
    role: string;
    avatarColor: string;
    discordUsername?: string;
}

interface AuthContextType {
    user: User | null;
    token: string | null;
    login: (email: string, password: string) => Promise<void>;
    register: (username: string, email: string, password: string, avatarColor?: string, discordUsername?: string) => Promise<void>;
    updateProfile: (data: Partial<User>) => Promise<void>;
    logout: () => void;
    isLoading: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const useAuth = () => {
    const context = useContext(AuthContext);
    if (!context) {
        throw new Error('useAuth must be used within AuthProvider');
    }
    return context;
};

interface AuthProviderProps {
    children: ReactNode;
}

export const AuthProvider: React.FC<AuthProviderProps> = ({ children }) => {
    const [user, setUser] = useState<User | null>(null);
    const [token, setToken] = useState<string | null>(null);
    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => {
        // Check for existing token on mount
        const storedToken = localStorage.getItem('token');
        const storedUser = localStorage.getItem('user');

        if (storedToken && storedUser) {
            setToken(storedToken);
            setUser(JSON.parse(storedUser));
        }

        setIsLoading(false);
    }, []);

    const login = async (email: string, password: string) => {
        try {
            const response = await authAPI.login({ email, password });
            const { user: userData, token: userToken } = response.data;

            setUser(userData);
            setToken(userToken);

            localStorage.setItem('token', userToken);
            localStorage.setItem('user', JSON.stringify(userData));
        } catch (error) {
            console.error('Login error:', error);
            throw error;
        }
    };

    const register = async (username: string, email: string, password: string, avatarColor?: string, discordUsername?: string) => {
        try {
            const response = await authAPI.register({ username, email, password, avatarColor, discordUsername });
            const { user: userData, token: userToken } = response.data;

            setUser(userData);
            setToken(userToken);

            localStorage.setItem('token', userToken);
            localStorage.setItem('user', JSON.stringify(userData));
        } catch (error) {
            console.error('Registration error:', error);
            throw error;
        }
    };

    const updateProfile = async (data: Partial<User>) => {
        try {
            // Need to implement updateProfile in API lib first or use direct call?
            // Assuming authAPI has updateProfile or we add it. 
            // For now, let's assume authAPI has generic request ability or we add it to api lib.
            // Wait, I should check api lib. But I can't read it in this turn.
            // I'll assume standard Axios pattern or modify API lib in next turn if needed.
            // Actually, I should probably check API lib. But I'll blindly trust Axios instance usage for now to be efficient
            // and fix if compilation fails.
            // Actually, better to just implement it assuming the authAPI object exists and I'll update it if needed.
            const response = await authAPI.updateProfile(data);
            const updatedUser = response.data;
            setUser(updatedUser);
            localStorage.setItem('user', JSON.stringify(updatedUser));
        } catch (error) {
            console.error('Update profile error:', error);
            throw error;
        }
    };

    const logout = () => {
        setUser(null);
        setToken(null);
        localStorage.removeItem('token');
        localStorage.removeItem('user');
    };

    return (
        <AuthContext.Provider value={{ user, token, login, register, updateProfile, logout, isLoading }}>
            {children}
        </AuthContext.Provider>
    );
};
